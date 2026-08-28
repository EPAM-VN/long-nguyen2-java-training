package epam.training.demo;

import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.project.dto.ProjectResponse;
import epam.training.demo.project.dto.ProjectUpdateRequest;
import epam.training.demo.security.dto.LoginRequest;
import epam.training.demo.security.dto.LoginResponse;
import epam.training.demo.security.dto.RegisterRequest;
import epam.training.demo.security.dto.UserResponse;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// This is the test 11.9/11.10's own comments pointed at: those @WebMvcTest
// slices could prove 403 but never the real 401 body, because their local
// method-security config never imports the real SecurityFilterChain. This
// class boots the actual full app - real SecurityConfig, real
// ProblemDetailAuthenticationEntryPoint, real JWT issuance/validation -
// over real HTTP.
// TestRestTemplate is no longer auto-registered by webEnvironment =
// RANDOM_PORT alone in this Spring Boot version - it moved to its own
// spring-boot-resttestclient module and needs this annotation explicitly
// (found by running it without this first and reading the
// NoSuchBeanDefinitionException).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProjectOwnershipE2ETest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private static final String PASSWORD = "password123";

    private String registerAndLogin(String usernamePrefix) {
        String username = usernamePrefix + "-" + UUID.randomUUID();

        ResponseEntity<UserResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                new RegisterRequest(username, username + "@example.com", PASSWORD),
                UserResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, PASSWORD), LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return loginResponse.getBody().token();
    }

    private <T> ResponseEntity<T> authorized(HttpMethod method, String url, String token, Object body,
                                              Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), responseType);
    }

    private Long createProject(String token, String name) {
        ResponseEntity<ProjectResponse> response = authorized(HttpMethod.POST, "/api/projects", token,
                new ProjectCreateRequest(name, "description"), ProjectResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    @Test
    void nonOwner_getsProject_returns403WithProblemDetailBody() {
        String aliceToken = registerAndLogin("alice");
        Long projectId = createProject(aliceToken, "Alice's Project");

        String bobToken = registerAndLogin("bob");

        ResponseEntity<Map> response = authorized(HttpMethod.GET, "/api/projects/" + projectId, bobToken, null,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody()).containsKey("detail");
    }

    @Test
    void noAuthorizationHeader_returns401WithProblemDetailBody() {
        String aliceToken = registerAndLogin("alice");
        Long projectId = createProject(aliceToken, "Alice's Project");

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/projects/" + projectId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody()).containsKey("detail");
    }

    @Test
    void owner_getPutDelete_allSucceed() {
        String aliceToken = registerAndLogin("alice");
        Long projectId = createProject(aliceToken, "Alice's Project");

        ResponseEntity<ProjectResponse> getResponse = authorized(
                HttpMethod.GET, "/api/projects/" + projectId, aliceToken, null, ProjectResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ProjectResponse> putResponse = authorized(
                HttpMethod.PUT, "/api/projects/" + projectId, aliceToken,
                new ProjectUpdateRequest("Updated Name", "Updated description"), ProjectResponse.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody().name()).isEqualTo("Updated Name");

        ResponseEntity<Void> deleteResponse = authorized(
                HttpMethod.DELETE, "/api/projects/" + projectId, aliceToken, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void admin_canAccessSomeoneElsesProject() {
        String ownerToken = registerAndLogin("owner");
        Long projectId = createProject(ownerToken, "Owner's Project");

        // Promoting to ADMIN via a direct repository save, not a real
        // admin-grant endpoint (none exists) - fine for test setup, not
        // something production code would ever do. The token has to be
        // minted AFTER the promotion: AuthService.login() bakes the
        // caller's roles into the JWT's "roles" claim at issuance time from
        // whatever's in the database right then, so logging in before the
        // promotion would still hand back a USER-only token.
        String adminUsername = "admin-" + UUID.randomUUID();
        ResponseEntity<UserResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                new RegisterRequest(adminUsername, adminUsername + "@example.com", PASSWORD),
                UserResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User adminUser = userRepository.findByUsername(adminUsername).orElseThrow();
        adminUser.setRoles(Set.of(Role.USER, Role.ADMIN));
        userRepository.save(adminUser);

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(adminUsername, PASSWORD), LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String adminToken = loginResponse.getBody().token();

        ResponseEntity<ProjectResponse> response = authorized(
                HttpMethod.GET, "/api/projects/" + projectId, adminToken, null, ProjectResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(projectId);
    }
}
