package org.pms.silverocean.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatesValidatorTest {
    private final CoordinatesValidator validator = new CoordinatesValidator();

    @Test
    void acceptsBoundedLatitudeAndLongitude() {
        assertTrue(validator.isValid("-1.286389, 36.817223", null));
        assertTrue(validator.isValid("90, -180", null));
    }

    @Test
    void rejectsMalformedOrOutOfRangeCoordinates() {
        assertFalse(validator.isValid("91, 36", null));
        assertFalse(validator.isValid("-1, 181", null));
        assertFalse(validator.isValid("NaN, 36", null));
        assertFalse(validator.isValid("1", null));
        assertFalse(validator.isValid(null, null));
    }
}
