package epam.training.demo.project;

import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.project.dto.ProjectCreateRequest;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Tried webEnvironment = NONE first, as this step describes - the whole
// context failed to load. SecurityConfig.authenticationManager() needs an
// AuthenticationConfiguration bean, which Spring Security only registers
// under a web application context; @SpringBootTest eagerly instantiates
// every singleton (AuthController/AuthService included, neither of which
// this test uses), so that one missing bean took the entire context down.
// MOCK (the default) still starts no real HTTP server or port - it's not
// a step back to a real webEnvironment, just enough of a web application
// context for Spring Security's own autoconfiguration to register that
// bean. Deliberately NOT @Transactional either way - a @DataJpaTest-style
// auto-rollback would hide the exact thing this test exists to prove
// (that ProjectService.createBatchUnsafe's partial commits are REAL,
// visible to a separate connection/transaction, not just visible within
// the same not-yet-committed one).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TransactionBoundaryTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectBatchService projectBatchService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    // Both batch methods derive the owner from Authentication.getName() via
    // ProjectService.create(), which requires a real, already-persisted
    // User - a random username with no matching row would 404 before
    // either transactional behavior is ever exercised.
    @BeforeEach
    void createOwner() {
        owner = new User();
        owner.setUsername("txn-owner-" + UUID.randomUUID());
        owner.setEmail(owner.getUsername() + "@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(Instant.now());
        owner.setRoles(Set.of(Role.USER));
        owner = userRepository.save(owner);
    }

    // createBatchUnsafe doesn't roll back, so it can leave real committed
    // Project rows behind tied to this test's owner. Since this class
    // isn't @Transactional, nothing undoes that automatically - left
    // alone, reruns would accumulate garbage rows and, since
    // users.username is unique (uq_users_username, V1) while
    // projects.name isn't, the owner row itself would eventually collide
    // on rerun even before the leftover projects became a problem. This
    // cleans up both every time, regardless of which test actually left
    // something behind.
    @AfterEach
    void cleanUp() {
        projectRepository.deleteAll(projectRepository.findAllWithTasksByOwnerUsername(owner.getUsername()));
        userRepository.delete(owner);
    }

    @Test
    @DisplayName("ProjectService.createBatchUnsafe: self-invocation defeats @Transactional - partial commits survive the thrown exception")
    void createBatchUnsafe_partialCommitsSurvive() {
        Authentication authentication = new TestingAuthenticationToken(owner.getUsername(), null);
        List<ProjectCreateRequest> requests = List.of(
                new ProjectCreateRequest("Unsafe Project 1", "d"),
                new ProjectCreateRequest("Unsafe Project 2", "d"),
                new ProjectCreateRequest("Unsafe Project 3", "d"));

        long before = projectRepository.count();

        assertThatThrownBy(() -> projectService.createBatchUnsafe(requests, authentication))
                .isInstanceOf(RuntimeException.class);

        long after = projectRepository.count();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("ProjectBatchService.createBatch: a real @Transactional boundary rolls back everything on the thrown exception")
    void createBatch_rollsBackEverything() {
        Authentication authentication = new TestingAuthenticationToken(owner.getUsername(), null);
        List<ProjectCreateRequest> requests = List.of(
                new ProjectCreateRequest("Safe Project 1", "d"),
                new ProjectCreateRequest("Safe Project 2", "d"),
                new ProjectCreateRequest("Safe Project 3", "d"));

        long before = projectRepository.count();

        assertThatThrownBy(() -> projectBatchService.createBatch(requests, authentication))
                .isInstanceOf(RuntimeException.class);

        long after = projectRepository.count();
        assertThat(after).isEqualTo(before);
    }
}
