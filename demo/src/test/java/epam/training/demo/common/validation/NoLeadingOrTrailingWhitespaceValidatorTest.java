package epam.training.demo.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NoLeadingOrTrailingWhitespaceValidatorTest {

    private final NoLeadingOrTrailingWhitespaceValidator validator = new NoLeadingOrTrailingWhitespaceValidator();

    // context is never touched by the implementation (it returns before using
    // it either way), so null is safe to pass here without a mock.
    @ParameterizedTest
    @ValueSource(strings = {"clean", "no whitespace here", ""})
    @DisplayName("value with no leading or trailing whitespace is valid")
    void noLeadingOrTrailingWhitespace_isValid(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {" leading", "trailing ", " both ", "\ttab", "newline\n"})
    @DisplayName("value with leading and/or trailing whitespace is invalid")
    void leadingOrTrailingWhitespace_isInvalid(String value) {
        assertThat(validator.isValid(value, null)).isFalse();
    }

    @Test
    void nullValue_isValid() {
        // @NotBlank's job, not this validator's - a validator is only asked
        // about non-null values in practice, but returning true for null
        // keeps this constraint composable with @NotBlank rather than
        // producing a duplicate/confusing violation for the same null.
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
