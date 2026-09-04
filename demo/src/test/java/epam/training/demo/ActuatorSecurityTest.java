package epam.training.demo;

import epam.training.demo.security.dto.LoginRequest;
import epam.training.demo.security.dto.LoginResponse;
import epam.training.demo.security.dto.RegisterRequest;
import epam.training.demo.security.dto.UserResponse;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Same real-HTTP, real-filter-chain shape as ProjectOwnershipE2ETest - the
// thing under test here (which URL pattern in SecurityConfig a request
// matches, and whether show-details actually reads the caller's role) only
// exists in the real SecurityFilterChain, not in any @WebMvcTest slice.
//
// @AutoConfigureMetrics: Spring Boot Test disables metrics EXPORT by
// default under @SpringBootTest (management.defaults.metrics.export.enabled
// = false, wired in by spring-boot-starter-actuator-test) - the safe
// default so a test run never accidentally pushes real metrics to a real
// backend. That's a narrower thing than "does MeterRegistry record
// anything" (counters/timers still work fine without this, which is why
// TaskAuditListenerTest/TaskConflictLogServiceIntegrationTest/
// ProductivityTipClientTest never needed it) - but
// PrometheusMetricsExportAutoConfiguration itself, the endpoint under test
// below, is gated behind that same export-enabled condition and simply
// never registers without this annotation, producing a 404-turned-500
// (NoResourceFoundException falling through to the catch-all handler) that
// looks nothing like a security failure.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@AutoConfigureMetrics
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ActuatorSecurityTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private static final String PASSWORD = "password123";

    private String registerAndLogin(String usernamePrefix, Set<Role> roles) {
        String username = usernamePrefix + "-" + UUID.randomUUID();

        ResponseEntity<UserResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                new RegisterRequest(username, username + "@example.com", PASSWORD),
                UserResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        if (roles.contains(Role.ADMIN)) {
            User user = userRepository.findByUsername(username).orElseThrow();
            user.setRoles(roles);
            userRepository.save(user);
        }

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, PASSWORD), LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return loginResponse.getBody().token();
    }

    private <T> ResponseEntity<T> withAuth(String url, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    @Test
    void health_noAuth_returnsUpWithNoComponentDetails() {
        ResponseEntity<Map> response = withAuth("/actuator/health", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody()).doesNotContainKey("components");
    }

    @Test
    void health_asAdmin_includesComponentDetails() {
        String adminToken = registerAndLogin("admin", Set.of(Role.USER, Role.ADMIN));

        ResponseEntity<Map> response = withAuth("/actuator/health", adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("components");
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).containsKey("db");
    }

    // show-details: when-authorized + roles: ADMIN is a stricter gate than
    // "any logged-in user" - a plain USER token is authenticated (passes
    // the permitAll URL matcher fine) but still isn't shown component
    // details, only the bare status. Proves the "roles: ADMIN" property
    // actually does something, not just "show-details: when-authorized" on
    // its own.
    @Test
    void health_asPlainUser_stillNoComponentDetails() {
        String userToken = registerAndLogin("user", Set.of(Role.USER));

        ResponseEntity<Map> response = withAuth("/actuator/health", userToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContainKey("components");
    }

    @Test
    void info_noAuth_returns401() {
        ResponseEntity<Map> response = withAuth("/actuator/info", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void info_asPlainUser_returns403() {
        String userToken = registerAndLogin("user", Set.of(Role.USER));

        ResponseEntity<Map> response = withAuth("/actuator/info", userToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void info_asAdmin_returnsBuildDetails() {
        String adminToken = registerAndLogin("admin", Set.of(Role.USER, Role.ADMIN));

        ResponseEntity<Map> response = withAuth("/actuator/info", adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> build = (Map<String, Object>) response.getBody().get("build");
        assertThat(build).isNotNull();
        assertThat(build.get("artifact")).isEqualTo("demo");
    }

    @Test
    void metrics_asAdmin_returns200() {
        String adminToken = registerAndLogin("admin", Set.of(Role.USER, Role.ADMIN));

        ResponseEntity<Map> response = withAuth("/actuator/metrics", adminToken, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Iterable<String>) response.getBody().get("names")).isNotEmpty();
    }

    // /actuator/prometheus falls under the same "/actuator/**" -> ADMIN
    // rule as info/metrics (SecurityConfig) - no separate matcher needed,
    // this just confirms it wasn't accidentally left off that rule's scope.
    @Test
    void prometheus_noAuth_returns401() {
        ResponseEntity<String> response = withAuth("/actuator/prometheus", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void prometheus_asPlainUser_returns403() {
        String userToken = registerAndLogin("user", Set.of(Role.USER));

        ResponseEntity<String> response = withAuth("/actuator/prometheus", userToken, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void prometheus_asAdmin_returnsTextExpositionFormat() {
        String adminToken = registerAndLogin("admin", Set.of(Role.USER, Role.ADMIN));

        ResponseEntity<String> response = withAuth("/actuator/prometheus", adminToken, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // jvm_memory_used_bytes is one of Micrometer's built-in JVM
        // metrics, auto-registered the moment spring-boot-starter-actuator
        // is on the classpath - present regardless of anything this app's
        // own code does, so its presence proves this is real Prometheus
        // text-exposition output, not an empty/broken registry.
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }
}
