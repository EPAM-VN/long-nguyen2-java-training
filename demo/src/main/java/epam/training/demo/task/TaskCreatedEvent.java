package epam.training.demo.task;

import java.time.Instant;

public record TaskCreatedEvent(Long taskId, Long projectId, String title, Instant occurredAt) {
}
