package codeit.ootd.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteSizeValidator
        implements ConstraintValidator<Utf8ByteSize, String> {

    private int max;

    @Override
    public void initialize(Utf8ByteSize annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        if (value == null) {
            return true; // null은 @NotBlank에서 검증
        }

        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
