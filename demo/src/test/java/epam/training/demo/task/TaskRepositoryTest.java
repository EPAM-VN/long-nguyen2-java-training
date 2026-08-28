package epam.training.demo.task;

import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.project.Project;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TaskRepositoryTest {

    // Same fix as DatabaseSmokeTest/ProjectRepositoryTest - each
    // @DataJpaTest class bootstraps its own context, and DemoApplication's
    // own static UTC-forcing block never runs under ASM-based
    // @SpringBootConfiguration resolution.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Project persistProject() {
        User owner = new User();
        owner.setUsername("alice");
        owner.setEmail("alice@example.com");
        owner.setPasswordHash("hash");
        owner.setCreatedAt(Instant.now());
        owner.setRoles(Set.of(Role.USER));
        entityManager.persist(owner);

        Project project = new Project();
        project.setName("Project");
        project.setDescription("description");
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        return entityManager.persistAndFlush(project);
    }

    private Task persistTask(Project project, String title, TaskStatus status) {
        Task task = new Task();
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(Priority.MEDIUM);
        task.setCreatedAt(Instant.now());
        task.setProject(project);
        return entityManager.persistAndFlush(task);
    }

    @Test
    @DisplayName("countByStatusForProject() groups by status via a JPQL constructor expression")
    void countByStatusForProject_groupsCountsByStatus() {
        Project project = persistProject();
        persistTask(project, "Task 1", TaskStatus.TODO);
        persistTask(project, "Task 2", TaskStatus.TODO);
        persistTask(project, "Task 3", TaskStatus.IN_PROGRESS);
        persistTask(project, "Task 4", TaskStatus.DONE);
        entityManager.clear();

        // "select new epam.training.demo.task.TaskStatusCount(...)" is a
        // plain string as far as javac is concerned - a typo in that
        // fully-qualified class name would only ever surface here, at
        // query-parse time against a real database, never at compile time.
        List<TaskStatusCount> counts = taskRepository.countByStatusForProject(project.getId());

        assertThat(counts)
                .extracting(TaskStatusCount::status, TaskStatusCount::count)
                .containsExactlyInAnyOrder(
                        tuple(TaskStatus.TODO, 2L),
                        tuple(TaskStatus.IN_PROGRESS, 1L),
                        tuple(TaskStatus.DONE, 1L));
    }

    @Test
    @DisplayName("searchByTitleForProject() is case-insensitive via ILIKE - a Postgres-only native query")
    void searchByTitleForProject_isCaseInsensitive() {
        Project project = persistProject();
        persistTask(project, "Quarterly report", TaskStatus.TODO);
        entityManager.clear();

        // ILIKE is Postgres-specific - this is the exact case that would
        // either fail outright or silently behave differently (case
        // sensitively) against H2, which is the concrete payoff for
        // running this against a real Postgres container instead.
        List<Task> results = taskRepository.searchByTitleForProject(project.getId(), "REPORT");

        assertThat(results).extracting(Task::getTitle).containsExactly("Quarterly report");
    }

    @Test
    @DisplayName("findByProjectId(Long, Class<T>) projection exposes id/title/status via the interface accessors")
    void findByProjectId_projection_exposesFields() {
        Project project = persistProject();
        Task saved = persistTask(project, "Projected Task", TaskStatus.IN_PROGRESS);
        entityManager.clear();

        List<TaskSummary> summaries = taskRepository.findByProjectId(project.getId(), TaskSummary.class);

        // These are Spring Data proxies over the projection interface, not
        // real Task/TaskSummary objects - equals()/contains() on them is
        // meaningless, so read the fields through the accessors instead.
        assertThat(summaries)
                .extracting(TaskSummary::getId, TaskSummary::getTitle, TaskSummary::getStatus)
                .containsExactly(tuple(saved.getId(), "Projected Task", TaskStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("Friction #1, again, now against a real query result: entity equality still lies")
    void entityEquality_stillPassesSpuriouslyAgainstRealQueryResults() {
        Project project = persistProject();
        Task saved = persistTask(project, "Real Title", TaskStatus.TODO);
        entityManager.clear();

        List<Task> tasks = taskRepository.findByProjectId(project.getId());

        // First attempt used a someUnsavedTask with a null id, expecting it
        // to "match" the real row the same way the earlier mocked-service
        // version did - it didn't, and AssertJ said so directly: no match
        // found. Against a REAL database, the saved Task has a real,
        // non-null generated id, so null.equals(realId) is correctly
        // false. The mocked version only ever passed because a mock never
        // assigns a real id, leaving both sides null - that's not
        // "equality lies", that's just two nulls.
        // The real trap is more insidious: give the unsaved object the
        // SAME id as a genuine row (as a stale cache entry, a
        // half-built update DTO, or a copy-paste bug might), with a wrong
        // title - Task.equals() (@EqualsAndHashCode(of = "id")) only ever
        // looks at id, so contains() reports a match anyway.
        Task someTaskWithMatchingIdButWrongTitle = new Task();
        someTaskWithMatchingIdButWrongTitle.setId(saved.getId());
        someTaskWithMatchingIdButWrongTitle.setTitle("Wrong Title");
        assertThat(tasks).contains(someTaskWithMatchingIdButWrongTitle);

        // The only assertion here that actually proves anything about the
        // data.
        assertThat(tasks).extracting(Task::getTitle).containsExactly("Real Title");
    }
}
