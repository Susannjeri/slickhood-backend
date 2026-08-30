package org.pms.silverocean.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CoordinatesValidator.class)
public @interface ValidCoordinates {
    String message() default "Coordinates must be valid latitude and longitude values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
