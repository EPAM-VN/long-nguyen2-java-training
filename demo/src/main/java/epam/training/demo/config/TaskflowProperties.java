package epam.training.demo.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("taskflow")
@Validated
public record TaskflowProperties(
        @Positive int defaultPageSize,
        @Positive int maxPageSize
) {

    public TaskflowProperties {
        if (defaultPageSize > maxPageSize) {
            throw new IllegalStateException(
                    "taskflow.default-page-size (%d) must not exceed taskflow.max-page-size (%d)"
                            .formatted(defaultPageSize, maxPageSize));
        }
    }
}
