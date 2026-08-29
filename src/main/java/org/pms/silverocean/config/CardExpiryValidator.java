package org.pms.silverocean.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CardExpiryValidator implements ConstraintValidator<ValidCardExpiry, String> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM/yy");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || !value.matches("^(0[1-9]|1[0-2])/[0-9]{2}$")) {
            return false;
        }

        try {
            YearMonth expiry = YearMonth.parse(value, FORMATTER);
            YearMonth now = YearMonth.now();
            return !expiry.isBefore(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}

