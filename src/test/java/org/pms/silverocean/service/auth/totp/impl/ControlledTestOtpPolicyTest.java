package org.pms.silverocean.service.auth.totp.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledTestOtpPolicyTest {
    @Test
    void disabledPolicyNeverOverridesRealOrSyntheticUsers() {
        ControlledTestOtpPolicy policy = new ControlledTestOtpPolicy(false,
                "mr.bean@qa.slickhood.test=482731");
        assertTrue(policy.codeFor("mr.bean@qa.slickhood.test").isEmpty());
    }

    @Test
    void enabledPolicyMatchesOnlyAnExactSyntheticIdentity() {
        ControlledTestOtpPolicy policy = new ControlledTestOtpPolicy(true,
                "mr.bean@qa.slickhood.test=482731");
        assertEquals("482731", policy.codeFor("MR.BEAN@QA.SLICKHOOD.TEST").orElseThrow());
        assertTrue(policy.codeFor("other@qa.slickhood.test").isEmpty());
        assertTrue(policy.codeFor("customer@example.com").isEmpty());
    }

    @Test
    void enabledPolicyRejectsRealEmailDomainsAndMalformedCodes() {
        assertThrows(IllegalStateException.class,
                () -> new ControlledTestOtpPolicy(true, "customer@example.com=482731"));
        assertThrows(IllegalStateException.class,
                () -> new ControlledTestOtpPolicy(true, "mr.bean@qa.slickhood.test=1234"));
        assertThrows(IllegalStateException.class,
                () -> new ControlledTestOtpPolicy(true, ""));
    }
}
