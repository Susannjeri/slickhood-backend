package org.pms.silverocean.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CardExpiryValidator.class)
public @interface ValidCardExpiry {
    String message() default "Invalid or expired card expiry date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
