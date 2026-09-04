package epam.training.demo.task;

import epam.training.demo.common.NotFoundException;
import epam.training.demo.project.Project;
import epam.training.demo.project.ProjectService;
import epam.training.demo.task.dto.TaskCreateRequest;
import epam.training.demo.task.dto.TaskUpdateRequest;
import epam.training.demo.user.User;
import epam.training.demo.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, ProjectService projectService, UserRepository userRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<Task> findAll(Long projectId, TaskStatus status, Priority priority, String keyword, Pageable pageable) {
        projectService.findById(projectId); // 404s if the project doesn't exist

        Specification<Task> spec = Specification.allOf(
                TaskSpecifications.hasProjectId(projectId),
                TaskSpecifications.hasStatus(status),
                TaskSpecifications.hasPriority(priority),
                TaskSpecifications.titleContains(keyword));

        return taskRepository.findAll(spec, pageable);
    }

    @Transactional
    public Task create(Long projectId, TaskCreateRequest request) {
        Project project = projectService.findById(projectId);
        User assignee = resolveAssignee(request.assigneeId());

        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status() != null ? request.status() : TaskStatus.TODO);
        task.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        task.setDueDate(request.dueDate());
        task.setAssignee(assignee);
        task.setCreatedAt(Instant.now());
        task.setProject(project);

        Task saved = taskRepository.save(task);
        // IDENTITY generation (Task.id) flushes the insert immediately, so
        // saved.getId() is already populated here, before this method (and
        // its transaction) returns.
        eventPublisher.publishEvent(new TaskCreatedEvent(saved.getId(), projectId, saved.getTitle(), Instant.now()));
        return saved;
    }

    @Transactional
    public Task update(Long projectId, Long taskId, TaskUpdateRequest request) {
        Task task = findByIdForProject(projectId, taskId);

        if (!request.version().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, taskId);
        }

        User assignee = resolveAssignee(request.assigneeId());

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setAssignee(assignee);

        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long projectId, Long taskId) {
        Task task = findByIdForProject(projectId, taskId);
        taskRepository.delete(task);
    }

    private Task findByIdForProject(Long projectId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task %d not found".formatted(taskId)));
        if (!task.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Task %d not found in project %d".formatted(taskId, projectId));
        }
        return task;
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(assigneeId)));
    }
}
