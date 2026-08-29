package org.pms.silverocean.controller.wrappers;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registrationAndResetApplyTheSameStrongPasswordPolicy() {
        RegistrationDTO registration = new RegistrationDTO();
        registration.setFullName("Test User");
        registration.setEmail("test@example.com");
        registration.setPassword("alllowercase1");

        VerifyOtpDTO reset = new VerifyOtpDTO();
        reset.setCode("A1B2C3");
        reset.setPassword("alllowercase1");

        assertFalse(validator.validate(registration).isEmpty());
        assertFalse(validator.validate(reset).isEmpty());

        registration.setPassword("StrongPass1!");
        reset.setPassword("StrongPass1!");
        assertTrue(validator.validate(registration).stream().noneMatch(v -> v.getPropertyPath().toString().equals("password")));
        assertTrue(validator.validate(reset).stream().noneMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void otpVerificationWithoutPasswordRemainsValidForNonResetJourneys() {
        VerifyOtpDTO verification = new VerifyOtpDTO();
        verification.setCode("A1B2C3");
        assertTrue(validator.validate(verification).isEmpty());
    }
}
