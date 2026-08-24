package epam.training.demo.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoLeadingOrTrailingWhitespaceValidator
        implements ConstraintValidator<NoLeadingOrTrailingWhitespace, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.equals(value.strip());
    }
}
