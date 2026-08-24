package epam.training.demo.task.dto;

import epam.training.demo.common.validation.NoLeadingOrTrailingWhitespace;
import epam.training.demo.task.Priority;
import epam.training.demo.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskUpdateRequest(

        @NotBlank
        @Size(max = 200)
        @NoLeadingOrTrailingWhitespace
        String title,

        @Size(max = 2000)
        String description,

        @NotNull
        TaskStatus status,

        @NotNull
        Priority priority,

        LocalDate dueDate,

        Long assigneeId
) {
}
