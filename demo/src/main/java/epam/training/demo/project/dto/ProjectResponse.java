package epam.training.demo.project.dto;

import java.time.Instant;

// Mapped from Project by ProjectMapper (MapStruct) - see that interface for
// the ownerId/taskCount derivation.
public record ProjectResponse(Long id, String name, String description, Long ownerId, Instant createdAt,
                               int taskCount) {
}
