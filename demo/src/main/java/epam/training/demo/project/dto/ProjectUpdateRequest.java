package epam.training.demo.project.dto;

import epam.training.demo.common.validation.NoLeadingOrTrailingWhitespace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(

        @NotBlank
        @Size(max = 100)
        @NoLeadingOrTrailingWhitespace
        String name,

        @Size(max = 2000)
        String description
) {
}
