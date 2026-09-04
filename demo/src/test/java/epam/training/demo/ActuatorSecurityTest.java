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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
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
}
