package epam.training.demo.project.dto;

import epam.training.demo.project.Project;

import java.time.Instant;

public record ProjectResponse(Long id, String name, String description, Long ownerId, Instant createdAt,
                               int taskCount) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getCreatedAt(),
                project.getTasks().size());
    }
}
