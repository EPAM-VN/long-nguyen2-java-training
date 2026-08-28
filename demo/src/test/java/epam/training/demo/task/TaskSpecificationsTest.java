package epam.training.demo.task;

import epam.training.demo.TestcontainersConfiguration;
import epam.training.demo.project.Project;
import epam.training.demo.user.Role;
import epam.training.demo.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TaskSpecificationsTest {

    // Same fix as 11.7's repository tests - each @DataJpaTest class
    // bootstraps its own DemoApplication-as-@SpringBootConfiguration via
    // ASM reading, which never runs DemoApplication's own static
    // UTC-forcing block.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Project projectA;
    private Project projectB;

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setCreatedAt(Instant.now());
        user.setRoles(Set.of(Role.USER));
        return entityManager.persist(user);
    }

    private Project persistProject(String name, User owner) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("description");
        project.setOwner(owner);
        project.setCreatedAt(Instant.now());
        return entityManager.persistAndFlush(project);
    }

    private Task persistTask(Project project, String title, TaskStatus status, Priority priority) {
        Task task = new Task();
        task.setTitle(title);
        task.setStatus(status);
        task.setPriority(priority);
        task.setCreatedAt(Instant.now());
        task.setProject(project);
        return entityManager.persistAndFlush(task);
    }

    // Each test rolls back automatically (@DataJpaTest wraps every test
    // method in its own transaction), so re-seeding here per test doesn't
    // leak state across tests despite projectA/projectB being shared
    // instance fields.
    @BeforeEach
    void seedData() {
        User ownerA = persistUser("ownerA");
        User ownerB = persistUser("ownerB");
        projectA = persistProject("Project A", ownerA);
        projectB = persistProject("Project B", ownerB);

        persistTask(projectA, "Write quarterly report", TaskStatus.TODO, Priority.HIGH);
        persistTask(projectA, "Review pull request", TaskStatus.IN_PROGRESS, Priority.MEDIUM);
        persistTask(projectA, "Deploy release", TaskStatus.DONE, Priority.LOW);
        persistTask(projectA, "Fix login bug", TaskStatus.TODO, Priority.MEDIUM);
        persistTask(projectA, "Plan next sprint", TaskStatus.TODO, Priority.LOW);

        persistTask(projectB, "Unrelated task", TaskStatus.TODO, Priority.HIGH);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("hasProjectId() returns only the tasks belonging to that project")
    void hasProjectId_scopesToOneProject() {
        List<Task> result = taskRepository.findAll(TaskSpecifications.hasProjectId(projectA.getId()));

        assertThat(result).hasSize(5);
        assertThat(result).extracting(Task::getTitle).doesNotContain("Unrelated task");
    }

    @Test
    @DisplayName("hasStatus() filters to the matching status; hasStatus(null) is a no-op")
    void hasStatus_filtersOrIsNoOp() {
        List<Task> todoOnly = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.hasStatus(TaskStatus.TODO)));

        assertThat(todoOnly)
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Write quarterly report", "Fix login bug", "Plan next sprint");

        List<Task> anyStatus = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.hasStatus(null)));

        assertThat(anyStatus).hasSize(5);
    }

    @Test
    @DisplayName("hasPriority() filters to the matching priority; hasPriority(null) is a no-op")
    void hasPriority_filtersOrIsNoOp() {
        List<Task> mediumOnly = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.hasPriority(Priority.MEDIUM)));

        assertThat(mediumOnly)
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Review pull request", "Fix login bug");

        List<Task> anyPriority = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.hasPriority(null)));

        assertThat(anyPriority).hasSize(5);
    }

    @Test
    @DisplayName("titleContains() matches case-insensitively; titleContains(null) is a no-op")
    void titleContains_isCaseInsensitiveOrNoOp() {
        List<Task> matching = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.titleContains("RepORT")));

        assertThat(matching).extracting(Task::getTitle).containsExactly("Write quarterly report");

        List<Task> noOp = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.titleContains(null)));

        assertThat(noOp).hasSize(5);
    }

    @Test
    @DisplayName("all four specs combined with every optional filter null returns every task in the project")
    void unrestricted_combinedNullFilters_returnsEverything() {
        // The test that would catch a broken no-op: if any of
        // hasStatus/hasPriority/titleContains ever stopped returning
        // Specification.unrestricted() for a null argument and instead
        // built a predicate that happened to match nothing (or only
        // some rows), this would be the first thing to fail.
        List<Task> result = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.hasStatus(null),
                TaskSpecifications.hasPriority(null),
                TaskSpecifications.titleContains(null)));

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("titleContains()'s case-insensitivity agrees with searchByTitleForProject()'s ILIKE")
    void titleContains_agreesWithNativeIlikeSearch() {
        String mixedCaseKeyword = "RepORT";

        List<Task> viaSpecification = taskRepository.findAll(Specification.allOf(
                TaskSpecifications.hasProjectId(projectA.getId()),
                TaskSpecifications.titleContains(mixedCaseKeyword)));

        List<Task> viaNativeIlike = taskRepository.searchByTitleForProject(projectA.getId(), mixedCaseKeyword);

        assertThat(viaSpecification)
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrderElementsOf(
                        viaNativeIlike.stream().map(Task::getTitle).toList());
    }

    @Test
    @DisplayName("real pagination: total element count differs from a single page's content size")
    void pagination_totalElementsDiffersFromPageContentSize() {
        Page<Task> page = taskRepository.findAll(
                Specification.allOf(TaskSpecifications.hasProjectId(projectA.getId())),
                PageRequest.of(0, 2, Sort.by("title")));

        // 5 matching tasks, page size 2: the count query (getTotalElements)
        // and the page query (getContent) run separately and answer
        // different questions - conflating them is the classic mistake.
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isNotEqualTo((long) page.getContent().size());
    }
}
