package epam.training.demo.task.dto;

import epam.training.demo.task.Priority;
import epam.training.demo.task.Task;
import epam.training.demo.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        Long assigneeId,
        Long projectId,
        Instant createdAt
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getProject().getId(),
                task.getCreatedAt());
    }
}
