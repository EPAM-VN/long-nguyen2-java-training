package epam.training.demo.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.project.Project;
import epam.training.demo.project.ProjectRepository;
import epam.training.demo.task.dto.TaskCreateRequest;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Deliberately NOT @Transactional on this class - same reasoning as
// TransactionBoundaryTest. If the test method ran inside its own
// surrounding transaction, taskService.create()'s @Transactional would just
// join it (propagation REQUIRED) instead of opening and committing its own,
// so the transaction this test needs to actually commit never would - and
// an AFTER_COMMIT listener would never fire at all. webEnvironment = MOCK
// for the same reason it's used everywhere else in this suite:
// SecurityConfig.authenticationManager() needs a web application context to
// exist, even though this test never touches HTTP.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TaskAuditListenerTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    private User owner;
    private Project project;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setUsername("audit-owner-" + UUID.randomUUID());
        owner.setEmail(owner.getUsername() + "@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(Instant.now());
        owner.setRoles(Set.of(Role.USER));
        owner = userRepository.save(owner);

        project = new Project();
        project.setName("Audit Project");
        project.setDescription("d");
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        project = projectRepository.save(project);

        // ListAppender captures every ILoggingEvent, including its actual
        // OS thread name - independent of whatever pattern the active
        // logging config happens to render, so this works whether or not
        // the console pattern includes %thread.
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(TaskAuditListener.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(TaskAuditListener.class)).detachAppender(appender);
        // Same reasoning as TransactionBoundaryTest.cleanUp(): `project`
        // here is a detached reference from setUp()'s save() call, whose
        // in-memory `tasks` collection is still the empty ArrayList it had
        // before taskService.create() persisted a Task against it in a
        // completely separate persistence context. Deleting that stale
        // detached instance directly makes Hibernate try to reconcile its
        // (empty) collection against the DB's real state and blows up
        // cascading orphanRemoval. Re-querying fresh (tasks eagerly
        // fetched) first avoids that entirely.
        projectRepository.deleteAll(projectRepository.findAllWithTasksByOwnerUsername(owner.getUsername()));
        userRepository.delete(owner);
    }

    @Test
    @DisplayName("create() commits, then the audit listener runs asynchronously on taskAuditExecutor - not on the caller's thread")
    void create_firesAsyncAuditListenerAfterCommit() {
        String callingThreadName = Thread.currentThread().getName();
        // MeterRegistry is a Spring-managed singleton, potentially shared
        // (and its counters accumulated) across other test classes whose
        // application context Spring's test framework happens to cache and
        // reuse - a before/after delta is robust regardless of what ran
        // earlier; asserting count() == 1 outright would not be.
        double countBefore = meterRegistry.counter("task.created").count();

        TaskCreateRequest request = new TaskCreateRequest("Audit me", null, null, null, null, null);
        Task created = taskService.create(project.getId(), request);

        ILoggingEvent event = awaitSingleLogEvent();

        assertThat(event.getFormattedMessage())
                .contains(created.getId().toString())
                .contains("Audit me")
                .contains(project.getId().toString());
        assertThat(event.getThreadName()).startsWith("task-audit-");
        assertThat(event.getThreadName()).isNotEqualTo(callingThreadName);

        assertThat(meterRegistry.counter("task.created").count()).isEqualTo(countBefore + 1);
    }

    // @TransactionalEventListener(AFTER_COMMIT) + @Async means there's no
    // synchronous return value or Future to block on - the whole point is
    // that this happens off-thread, on its own schedule, after the caller
    // already got its response. A short bounded poll is the honest way to
    // assert on it: a fixed Thread.sleep would either be flaky (too short)
    // or needlessly slow the suite (too long) for no real benefit.
    private ILoggingEvent awaitSingleLogEvent() {
        Instant deadline = Instant.now().plusSeconds(3);
        while (Instant.now().isBefore(deadline)) {
            if (!appender.list.isEmpty()) {
                return appender.list.get(0);
            }
            sleep();
        }
        throw new AssertionError("Audit log event did not appear within 3 seconds");
    }

    private void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
