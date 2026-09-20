package org.pms.silverocean.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BusinessDateValidator.class)
public @interface BusinessDate {
    String message() default "date is outside the allowed business-date range";
    Direction direction() default Direction.PAST_OR_PRESENT;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    enum Direction {
        PAST_OR_PRESENT,
        FUTURE_OR_PRESENT
    }
}
