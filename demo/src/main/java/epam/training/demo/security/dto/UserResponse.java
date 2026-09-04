package epam.training.demo.security.dto;

import java.time.Instant;

// Mapped from User by UserMapper (MapStruct) - every field matches by name,
// so that interface needs no @Mapping customization at all.
public record UserResponse(Long id, String username, String email, Instant createdAt) {
}
