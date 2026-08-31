package org.pms.silverocean.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordinatesValidator implements ConstraintValidator<ValidCoordinates, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String[] coordinates = value.split(",", -1);
        if (coordinates.length != 2) {
            return false;
        }

        try {
            double latitude = Double.parseDouble(coordinates[0].trim());
            double longitude = Double.parseDouble(coordinates[1].trim());
            return Double.isFinite(latitude)
                    && Double.isFinite(longitude)
                    && latitude >= -90 && latitude <= 90
                    && longitude >= -180 && longitude <= 180;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
