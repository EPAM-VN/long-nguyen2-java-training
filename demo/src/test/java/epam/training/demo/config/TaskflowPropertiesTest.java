package epam.training.demo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskflowPropertiesTest {

    @Test
    @DisplayName("a valid pair constructs cleanly and accessors return what was passed in")
    void validPair_constructsCleanly() {
        TaskflowProperties properties = new TaskflowProperties(20, 100);

        assertThat(properties.defaultPageSize()).isEqualTo(20);
        assertThat(properties.maxPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("defaultPageSize greater than maxPageSize throws IllegalStateException")
    void defaultPageSizeGreaterThanMaxPageSize_throws() {
        assertThatThrownBy(() -> new TaskflowProperties(200, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not exceed");
    }

    // Observed: new TaskflowProperties(-1, 10) does NOT throw. The compact
    // constructor only checks defaultPageSize > maxPageSize (-1 > 10 is
    // false), and @Positive is a Jakarta Validation annotation - it's only
    // enforced by a Validator actually walking the constraints, which here
    // is Spring's @Validated wiring on the @ConfigurationProperties bean.
    // Calling `new TaskflowProperties(...)` directly, like this test does,
    // never goes through that machinery, so the annotation is silently
    // inert and a negative value sails through uncaught.
    @Test
    @DisplayName("negative defaultPageSize is not rejected by the record itself")
    void negativeDefaultPageSize_doesNotThrowWhenConstructedDirectly() {
        TaskflowProperties properties = new TaskflowProperties(-1, 10);

        assertThat(properties.defaultPageSize()).isEqualTo(-1);
    }
}
