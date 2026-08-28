package epam.training.demo.project;

import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.task.Priority;
import epam.training.demo.task.Task;
import epam.training.demo.task.TaskStatus;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import org.hibernate.LazyInitializationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProjectRepositoryTest {

    // Same fix as DatabaseSmokeTest, needed independently here: each
    // @DataJpaTest class gets its own bootstrap of DemoApplication as
    // @SpringBootConfiguration via ASM reading, which never runs
    // DemoApplication's own static UTC-forcing block.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(Instant.now());
        user.setRoles(Set.of(Role.USER));
        return entityManager.persist(user);
    }

    private Project newProject(String name, User owner) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("description");
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        return project;
    }

    private Task newTask(Project project, String title) {
        Task task = new Task();
        task.setTitle(title);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(Priority.MEDIUM);
        task.setCreatedAt(Instant.now());
        task.setProject(project);
        return task;
    }

    @Test
    @DisplayName("FAILING: a lazy association accessed after the owning entity is detached throws LazyInitializationException")
    void tasksAccessedAfterDetach_withoutJoinFetch_throwsLazyInitializationException() {
        User owner = persistUser("alice");
        Project project = entityManager.persistAndFlush(newProject("Lazy Test", owner));
        entityManager.persistAndFlush(newTask(project, "Task 1"));

        // First attempt didn't reproduce the failure: persisting `project`
        // before any task existed left its `tasks` PersistentBag marked
        // "already initialized, 0 elements" at that first flush - the
        // in-memory ArrayList really was empty at the time, so Hibernate
        // never needed to query for it, and detaching it afterward just
        // returns that stale, already-known-empty list, no exception. The
        // task added afterward never touches that in-memory collection
        // (only the FK on the Task side does) - it's still there,
        // "initialized," unaware anything changed.
        // Forcing a genuine uninitialized proxy needs a completely fresh
        // load: clear the persistence context, then re-find the entity by
        // id, which comes back as a real lazy proxy that has never been
        // touched.
        entityManager.clear();
        Project reloaded = entityManager.find(Project.class, project.getId());
        entityManager.detach(reloaded);

        // project.tasks is @OneToMany(mappedBy = "project"), LAZY by
        // default, and this reloaded proxy was never touched before
        // detach - so there is no initialized collection to read, and no
        // session left to fetch it from. This is exactly the failure
        // ProjectResponse.from(project) or any other post-transaction
        // access to an un-fetched association would hit in production,
        // which is why every repository method that actually needs
        // tasks/owner uses LEFT JOIN FETCH instead of a plain findById.
        assertThatThrownBy(() -> reloaded.getTasks().size())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    @DisplayName("WORKING: the same association, pre-initialized via JOIN FETCH, survives detach")
    void tasksAccessedAfterDetach_withJoinFetch_worksFine() {
        User owner = persistUser("alice");
        Project project = entityManager.persistAndFlush(newProject("Lazy Test", owner));
        entityManager.persistAndFlush(newTask(project, "Task 1"));
        entityManager.clear();

        // findByIdWithTasks's LEFT JOIN FETCH initializes the tasks
        // collection as part of the query itself, before the entity is
        // ever handed back - so it no longer matters that the entity gets
        // detached afterward. Same field, same laziness annotation, but
        // now safe to read.
        Project reloaded = projectRepository.findByIdWithTasks(project.getId()).orElseThrow();
        entityManager.detach(reloaded);

        assertThat(reloaded.getTasks()).hasSize(1);
    }

    @Test
    @DisplayName("findAllWithTasks() returns one row per project, not one per task, thanks to DISTINCT")
    void findAllWithTasks_returnsOneProjectWithAllItsTasks() {
        User owner = persistUser("alice");
        Project project = entityManager.persistAndFlush(newProject("Project With Tasks", owner));
        entityManager.persistAndFlush(newTask(project, "Task 1"));
        entityManager.persistAndFlush(newTask(project, "Task 2"));
        entityManager.persistAndFlush(newTask(project, "Task 3"));
        entityManager.flush();
        entityManager.clear();

        // Without DISTINCT, a LEFT JOIN FETCH p.tasks against a project
        // with 3 tasks returns 3 duplicate result rows (one per joined
        // task row) - the same Project object 3 times, not 3 different
        // projects.
        List<Project> result = projectRepository.findAllWithTasks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTasks()).hasSize(3);
    }

    @Test
    @DisplayName("findAllWithTasksByOwnerUsername() only returns the matching owner's projects")
    void findAllWithTasksByOwnerUsername_scopesToOwner() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");
        entityManager.persistAndFlush(newProject("Alice's Project", alice));
        entityManager.persistAndFlush(newProject("Bob's Project", bob));
        entityManager.flush();
        entityManager.clear();

        List<Project> aliceProjects = projectRepository.findAllWithTasksByOwnerUsername("alice");

        assertThat(aliceProjects).hasSize(1);
        assertThat(aliceProjects.get(0).getName()).isEqualTo("Alice's Project");
    }
}
