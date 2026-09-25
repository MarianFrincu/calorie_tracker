package com.calorietracker.backendcore.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/**
 * A diary/water/weight date a human could actually mean: from 2000-01-01 up to
 * one year ahead. Keeps typos like 0026 or 9999 out of the history (and out
 * of the report range maths). Null is left to {@code @NotNull}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PlausibleDate.Validator.class)
public @interface PlausibleDate {

    String message() default "must be between 2000-01-01 and one year from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    class Validator implements ConstraintValidator<PlausibleDate, LocalDate> {
        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            if (value == null) return true;
            return !value.isBefore(EARLIEST) && !value.isAfter(LocalDate.now().plusYears(1));
        }
    }
}
