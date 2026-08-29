package org.pms.silverocean.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PMSUtilsOtpTest {

    @Test
    void generatedEmailOtpIsAlwaysSixUppercaseAlphanumericCharacters() {
        for (int i = 0; i < 1_000; i++) {
            assertTrue(PMSUtils.generateRandomOTP().matches("[A-Z0-9]{6}"));
        }
    }
}
