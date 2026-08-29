package org.pms.silverocean.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // handle @NotNull separately
        try {
            Currency.getInstance(value); // throws IllegalArgumentException if invalid
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
