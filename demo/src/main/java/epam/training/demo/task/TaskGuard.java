package epam.training.demo.task;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Bean name defaults to "taskGuard" - what @PreAuthorize's SpEL references
// via @taskGuard.
@Component
public class TaskGuard {

    private final TaskRepository taskRepository;

    public TaskGuard(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Same reasoning as ProjectGuard.isOwner(): false, not a thrown
    // NotFoundException, when the task doesn't exist - keeps "no such
    // task" and "not yours" indistinguishable (both 403) to anyone probing
    // ids they don't own. Tasks have no owner field of their own -
    // ownership is inherited from the parent Project, so this walks
    // task.getProject().getOwner() instead of duplicating an owner column.
    @Transactional(readOnly = true)
    public boolean isOwner(Long taskId, Authentication authentication) {
        return taskRepository.findById(taskId)
                .map(task -> task.getProject().getOwner().getUsername().equals(authentication.getName()))
                .orElse(false);
    }
}
