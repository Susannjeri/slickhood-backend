package org.pms.silverocean.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.wrappers.LoginResponseDTO;
import org.pms.silverocean.controller.wrappers.VerifyOtpDTO;
import org.pms.silverocean.controller.wrappers.VerificationOptionsDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.LoginAttemptService;
import org.pms.silverocean.service.auth.UserAuthenticationService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OtpControllerTest {
    private TotpServiceFactory factory;
    private TotpService otp;
    private UserAuthenticationService authentication;
    private OtpController controller;

    @BeforeEach
    void setUp() {
        factory = mock(TotpServiceFactory.class);
        otp = mock(TotpService.class);
        authentication = mock(UserAuthenticationService.class);
        controller = new OtpController(factory, mock(I18NService.class), mock(LoginAttemptService.class), authentication);
    }

    @Test
    void successfulEmailVerificationReturnsCompleteSession() {
        VerifyOtpDTO request = new VerifyOtpDTO();
        request.setEmail("new.user@example.com");
        request.setCode("123456");
        request.setChannel(OtpType.EMAIL);
        LoginResponseDTO session = new LoginResponseDTO(false, false, "access-jwt", "refresh-token");
        when(factory.getService(OtpType.EMAIL)).thenReturn(Optional.of(otp));
        when(otp.validateVerificationToken(request.getEmail(), request.getCode())).thenReturn(true);
        when(authentication.createSessionForVerifiedUser(request.getEmail())).thenReturn(session);

        var response = controller.verify(request);

        assertTrue(response.getBody().isSuccess());
        assertEquals(session, response.getBody().getData().getFirst());
        verify(authentication).createSessionForVerifiedUser(request.getEmail());
    }

    @Test
    void publicRecoveryOptionsDoNotRevealAccountExistenceOrConfiguredFactors() {
        var response = controller.getVerificationOptions("unknown@example.com");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        var options = (org.pms.silverocean.controller.wrappers.VerificationOptionsDTO)
                response.getBody().getData().getFirst();
        assertTrue(options.email());
        assertEquals(false, options.phone());
        assertEquals(false, options.google());
        assertEquals("EMAIL", options.preferred());
        verifyNoInteractions(factory);
    }

    @Test
    void recoverySendDoesNotSendToUnknownAddressAndStillReturnsGenericSuccess() {
        when(factory.getUserVerificationOptions("unknown@example.com")).thenReturn(Optional.empty());

        var response = controller.sendOTP("unknown@example.com", OtpType.EMAIL);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isSuccess());
        verifyNoInteractions(otp);
    }

    @Test
    void recoverySendDeliversOnlyForEligibleEmailAccount() {
        when(factory.getUserVerificationOptions("known@example.com"))
                .thenReturn(Optional.of(new VerificationOptionsDTO(true, false, false, "EMAIL")));
        when(factory.getService(OtpType.EMAIL)).thenReturn(Optional.of(otp));

        var response = controller.sendOTP("known@example.com", OtpType.EMAIL);

        assertEquals(200, response.getStatusCode().value());
        verify(otp).generateOTPCode("known@example.com");
    }
}
