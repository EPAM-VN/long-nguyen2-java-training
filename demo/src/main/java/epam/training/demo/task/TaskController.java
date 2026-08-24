package epam.training.demo.task;

import epam.training.demo.project.ProjectService;
import epam.training.demo.task.dto.TaskCreateRequest;
import epam.training.demo.task.dto.TaskResponse;
import epam.training.demo.task.dto.TaskUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final ProjectService projectService;

    public TaskController(TaskRepository taskRepository, TaskService taskService, ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.projectService = projectService;
    }

    @GetMapping
    public Page<TaskResponse> getAll(@PathVariable Long projectId,
                                      @RequestParam(required = false) TaskStatus status,
                                      @RequestParam(required = false) Priority priority,
                                      @RequestParam(required = false) String keyword,
                                      Pageable pageable) {
        return taskService.findAll(projectId, status, priority, keyword, pageable)
                .map(TaskResponse::from);
    }

    @GetMapping("/status-counts")
    public List<TaskStatusCount> getStatusCounts(@PathVariable Long projectId) {
        projectService.findById(projectId);
        return taskRepository.countByStatusForProject(projectId);
    }

    @GetMapping("/search")
    public List<TaskResponse> search(@PathVariable Long projectId, @RequestParam String keyword) {
        projectService.findById(projectId);
        return taskRepository.searchByTitleForProject(projectId, keyword).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @GetMapping("/summary")
    public List<TaskSummary> getSummary(@PathVariable Long projectId) {
        projectService.findById(projectId);
        return taskRepository.findByProjectId(projectId, TaskSummary.class);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId,
                                                @Valid @RequestBody TaskCreateRequest request) {
        Task created = taskService.create(projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{taskId}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(TaskResponse.from(created));
    }

    @PutMapping("/{taskId}")
    public TaskResponse update(@PathVariable Long projectId,
                                @PathVariable Long taskId,
                                @Valid @RequestBody TaskUpdateRequest request) {
        return TaskResponse.from(taskService.update(projectId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long taskId) {
        taskService.delete(projectId, taskId);
        return ResponseEntity.noContent().build();
    }
}
