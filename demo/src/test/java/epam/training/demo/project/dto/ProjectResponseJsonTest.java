package epam.training.demo.project.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@ActiveProfiles("test")
class ProjectResponseJsonTest {

    @Autowired
    private JacksonTester<ProjectResponse> json;

    // Constructed directly via the record constructor, never through
    // ProjectMapper.toResponse(project) - this test only cares about
    // ProjectResponse's own JSON shape, not about how a Project turns into
    // one, so building the DTO directly keeps it independent of the mapper
    // (and of a real, fully-associated Project entity) entirely.
    @Test
    void serializesAllFields() throws Exception {
        ProjectResponse response = new ProjectResponse(
                1L, "Taskflow", "A project", 2L, Instant.parse("2026-08-27T10:00:00Z"), 3);

        var content = json.write(response);

        assertThat(content).hasJsonPathNumberValue("$.id");
        assertThat(content).extractingJsonPathNumberValue("$.id").isEqualTo(1);
        assertThat(content).extractingJsonPathStringValue("$.name").isEqualTo("Taskflow");
        assertThat(content).extractingJsonPathStringValue("$.description").isEqualTo("A project");
        assertThat(content).extractingJsonPathNumberValue("$.ownerId").isEqualTo(2);
        assertThat(content).extractingJsonPathNumberValue("$.taskCount").isEqualTo(3);
        assertThat(content).hasJsonPathValue("$.createdAt");
    }
}
