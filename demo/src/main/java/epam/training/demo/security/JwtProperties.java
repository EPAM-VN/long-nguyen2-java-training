package epam.training.demo.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// min = 32 chars isn't arbitrary - HS256 wants a key of at least 256 bits
// (32 bytes) or the JWT library rejects it at signing time with a much
// less readable error than this constraint's own message.
@ConfigurationProperties("jwt")
@Validated
public record JwtProperties(
        @NotBlank
        @Size(min = 32)
        String secret,

        @NotNull
        Duration expiration
) {
}
