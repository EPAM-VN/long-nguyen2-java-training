package epam.training.demo.task;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.Project;
import epam.training.demo.project.ProjectService;
import epam.training.demo.task.dto.TaskCreateRequest;
import epam.training.demo.task.dto.TaskUpdateRequest;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TaskConflictLogService taskConflictLogService;

    @InjectMocks
    private TaskService taskService;

    private Project projectWithId(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }

    private Task taskInProject(Long taskId, Long projectId, Long version) {
        Task task = new Task();
        task.setId(taskId);
        task.setProject(projectWithId(projectId));
        task.setVersion(version);
        task.setStatus(TaskStatus.TODO);
        task.setPriority(Priority.MEDIUM);
        return task;
    }

    @Test
    @DisplayName("findAll() builds a non-null Specification and delegates to the repository")
    void findAll_buildsSpecificationAndDelegates() {
        when(projectService.findById(1L)).thenReturn(projectWithId(1L));
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        taskService.findAll(1L, TaskStatus.TODO, Priority.HIGH, "keyword", Pageable.unpaged());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<Task>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(taskRepository).findAll(captor.capture(), any(Pageable.class));

        // This only proves findAll() constructs *some* Specification and
        // hands it to the repository - a mocked TaskRepository never
        // actually evaluates a Specification against anything, so this
        // can't prove the composed predicates (hasProjectId/hasStatus/
        // hasPriority/titleContains) filter correctly, or even reference
        // the right columns. That needs a real query executed against a
        // real EntityManager/database - see 11.8.
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("create() saves a Task built from the request, the resolved project, and no assignee")
    void create_savesTaskWithRequestFieldsAndProject() {
        Project project = projectWithId(1L);
        when(projectService.findById(1L)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskCreateRequest request = new TaskCreateRequest(
                "Write tests", "Cover TaskService", TaskStatus.IN_PROGRESS, Priority.HIGH,
                LocalDate.of(2026, 9, 1), null);

        Instant before = Instant.now();
        Task result = taskService.create(1L, request);
        Instant after = Instant.now();

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("Write tests");
        assertThat(saved.getDescription()).isEqualTo("Cover TaskService");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(saved.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(saved.getAssignee()).isNull();
        assertThat(saved.getProject()).isSameAs(project);
        assertThat(saved.getCreatedAt()).isBetween(before, after);
        assertThat(result).isSameAs(saved);

        // Proves create() publishes a TaskCreatedEvent for the saved task -
        // it says nothing about the listener side (async execution,
        // AFTER_COMMIT timing), which needs a real transaction/executor;
        // see TaskAuditListenerTest for that.
        ArgumentCaptor<TaskCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().title()).isEqualTo("Write tests");
        assertThat(eventCaptor.getValue().projectId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("create() when the project doesn't exist -> NotFoundException propagates, save() never called")
    void create_projectNotFound_propagatesAndNeverSaves() {
        when(projectService.findById(1L)).thenThrow(new NotFoundException("Project 1 not found"));

        TaskCreateRequest request = new TaskCreateRequest(
                "Write tests", null, null, null, null, null);

        assertThatThrownBy(() -> taskService.create(1L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Project 1 not found");

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() when the task belongs to a different project -> NotFoundException, save() never called")
    void update_taskInDifferentProject_throwsNotFound() {
        Task task = taskInProject(5L, 2L, 0L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, null, 0L);

        // findByIdForProject is private, reachable only through update()/delete() -
        // exercised here via update(), and again below via delete().
        assertThatThrownBy(() -> taskService.update(1L, 5L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Task 5 not found in project 1");

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() with a stale version -> ObjectOptimisticLockingFailureException, thrown before save()")
    void update_staleVersion_throwsOptimisticLockingFailureBeforeSave() {
        Task task = taskInProject(5L, 1L, 3L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, null, 2L);

        // This only proves the manual pre-check in update() rejects a
        // mismatched version before touching the repository - it says
        // nothing about whether @Version actually enforces optimistic
        // locking at the JPA/database level on a concurrent real save;
        // that needs a real EntityManager (11.7/11.11).
        assertThatThrownBy(() -> taskService.update(1L, 5L, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(taskRepository, never()).save(any());
        // Proves update() calls out to record the conflict at all, and with
        // the right values - it can't prove REQUIRES_NEW's actual point
        // (that this write survives the caller's transaction rolling back),
        // since a mocked TaskConflictLogService has no real transaction to
        // roll back in the first place; that needs a real
        // EntityManager/TransactionManager - see
        // TaskConflictLogServiceIntegrationTest.
        verify(taskConflictLogService).recordConflict(5L, 1L, 2L, 3L);
    }

    @Test
    @DisplayName("update() with assigneeId == null clears the assignee without looking up any user")
    void update_nullAssigneeId_noRepositoryLookup() {
        // Tried stubbing userRepository.findById(...) here first, even
        // though assigneeId == null means resolveAssignee() never calls it -
        // Mockito's strict stubbing failed the test with
        // UnnecessaryStubbingException instead of silently ignoring it.
        // Fixed by deleting the stub (not lenient()) since an unused stub
        // here would have been hiding a real bug, not a false positive.
        Task task = taskInProject(5L, 1L, 0L);
        task.setAssignee(new User());
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, null, 0L);

        Task result = taskService.update(1L, 5L, request);

        assertThat(result.getAssignee()).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("update() with an unknown assigneeId -> NotFoundException")
    void update_unknownAssigneeId_throwsNotFound() {
        Task task = taskInProject(5L, 1L, 0L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", null, TaskStatus.DONE, Priority.LOW, null, 99L, 0L);

        assertThatThrownBy(() -> taskService.update(1L, 5L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User 99 not found");

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() when the task belongs to a different project -> NotFoundException")
    void delete_taskInDifferentProject_throwsNotFound() {
        Task task = taskInProject(5L, 2L, 0L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.delete(1L, 5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Task 5 not found in project 1");

        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    @DisplayName("delete() happy path deletes the resolved task")
    void delete_found_deletesTask() {
        Task task = taskInProject(5L, 1L, 0L);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        taskService.delete(1L, 5L);

        verify(taskRepository).delete(task);
    }
}
