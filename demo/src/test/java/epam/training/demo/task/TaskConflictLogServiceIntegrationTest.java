package epam.training.demo.task;

import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.project.Project;
import epam.training.demo.project.ProjectRepository;
import epam.training.demo.task.dto.TaskUpdateRequest;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Deliberately NOT @Transactional on this class, same reasoning as
// TransactionBoundaryTest: if the test method itself opened a transaction,
// taskService.update() would just join it (PROPAGATION_REQUIRED, the
// default), and taskConflictLogService.recordConflict() would then be
// nested one level deeper inside THAT - REQUIRES_NEW would still suspend
// and open a genuinely separate transaction either way, but auto-rollback
// on the outer test transaction at the end would erase the evidence this
// test exists to check, making a real assertion impossible to trust.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskConflictLogServiceIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskConflictLogRepository taskConflictLogRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private User owner;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setUsername("cflt-owner-" + UUID.randomUUID());
        owner.setEmail(owner.getUsername() + "@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(Instant.now());
        owner.setRoles(Set.of(Role.USER));
        owner = userRepository.save(owner);

        project = new Project();
        project.setName("Conflict Project");
        project.setDescription("d");
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        project = projectRepository.save(project);

        task = new Task();
        task.setTitle("Original title");
        task.setStatus(TaskStatus.TODO);
        task.setPriority(Priority.MEDIUM);
        task.setCreatedAt(Instant.now());
        task.setProject(project);
        task = taskRepository.save(task);
    }

    @AfterEach
    void tearDown() {
        // task_conflict_logs.task_id has no ON DELETE CASCADE (V6) - it has
        // to be cleared before the Task row it references can be deleted,
        // same FK-ordering reasoning as everywhere else cleanup happens in
        // this suite.
        taskConflictLogRepository.deleteAll(taskConflictLogRepository.findByTaskId(task.getId()));
        projectRepository.deleteAll(projectRepository.findAllWithTasksByOwnerUsername(owner.getUsername()));
        userRepository.delete(owner);
    }

    @Test
    @DisplayName("update() with a stale version rolls back its own transaction, but the REQUIRES_NEW conflict log still commits")
    void update_staleVersion_conflictLogSurvivesCallersRollback() {
        // Delta, not an absolute count - MeterRegistry is a shared
        // singleton whose application context Spring's test framework may
        // cache and reuse across other test classes, same reasoning as
        // TaskAuditListenerTest's task.created assertion.
        double countBefore = meterRegistry.counter("task.conflict").count();

        Long staleVersion = task.getVersion() + 1;
        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, null, staleVersion);

        assertThatThrownBy(() -> taskService.update(project.getId(), task.getId(), request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(meterRegistry.counter("task.conflict").count()).isEqualTo(countBefore + 1);

        // Read back through a completely separate repository call/
        // transaction, after update()'s has already failed and rolled back
        // - if recordConflict() had joined that transaction instead of
        // opening its own (REQUIRES_NEW), this row would not be here.
        List<TaskConflictLog> logs = taskConflictLogRepository.findByTaskId(task.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getProjectId()).isEqualTo(project.getId());
        assertThat(logs.get(0).getAttemptedVersion()).isEqualTo(staleVersion);
        assertThat(logs.get(0).getActualVersion()).isEqualTo(task.getVersion());

        // The task itself proves the OTHER half of the story: update()'s
        // own transaction really did roll back (title/version in the DB
        // are exactly what setUp() persisted, untouched by the rejected
        // request) - the conflict log surviving isn't because nothing
        // rolled back at all, it's specifically because that one write was
        // in its own independent transaction.
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Original title");
        assertThat(reloaded.getVersion()).isEqualTo(task.getVersion());
    }

    @Test
    @DisplayName("update() with the correct version never records a conflict")
    void update_correctVersion_noConflictLogged() {
        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, null, task.getVersion());

        taskService.update(project.getId(), task.getId(), request);

        assertThat(taskConflictLogRepository.findByTaskId(task.getId())).isEmpty();
    }
}
