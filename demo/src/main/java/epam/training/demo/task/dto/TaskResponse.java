package epam.training.demo.task.dto;

import epam.training.demo.task.Priority;
import epam.training.demo.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

// Mapped from Task by TaskMapper (MapStruct) - see that interface for the
// assigneeId/projectId derivation.
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        Long assigneeId,
        Long projectId,
        Instant createdAt,
        Long version
) {
}
