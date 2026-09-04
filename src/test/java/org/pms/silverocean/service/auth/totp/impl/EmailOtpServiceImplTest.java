package org.pms.silverocean.service.auth.totp.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOtpServiceImplTest {
    private ConfigService config;
    private NotificationService notifications;
    private OTPEncryptionService encryption;
    private EmailOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        config = mock(ConfigService.class);
        notifications = mock(NotificationService.class);
        encryption = mock(OTPEncryptionService.class);
        I18NService i18n = mock(I18NService.class);
        when(config.getConfigByName(PMSConfigs.OTP_VALIDITY_SECONDS))
                .thenReturn(() -> new ConfigDTO(1L, "otp-validity", null, 300, false));
        when(i18n.getLocalizedMessage(any(String.class))).thenReturn("Code %s expires %s");
        service = new EmailOtpServiceImpl(mock(JwtService.class), config, notifications, encryption, i18n);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60);
    }

    @Test
    void resendDuringCooldownKeepsExistingCodeAndDoesNotQueueAnotherEmail() {
        when(encryption.hasRecentlyIssuedOTP("owner@example.com", "owner@example.com", OtpType.EMAIL, 60))
                .thenReturn(true);

        assertEquals("Use the most recently issued OTP", service.generateOTPCode("owner@example.com"));

        verify(encryption, never()).saveOTP(any(), any(), any(), any());
        verify(notifications, never()).sendNotification(any());
    }

    @Test
    void firstRequestCreatesCodeAndQueuesEmail() {
        service.generateOTPCode("owner@example.com");

        verify(encryption).saveOTP(any(), any(), any(), any());
        verify(notifications).sendNotification(any());
    }
}
