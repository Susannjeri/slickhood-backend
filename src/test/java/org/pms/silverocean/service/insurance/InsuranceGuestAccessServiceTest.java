package org.pms.silverocean.service.insurance;

import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.InsuranceGuestAccessRepo;
import org.pms.silverocean.database.pms.entities.InsuranceGuestAccess;
import org.pms.silverocean.database.pms.entities.Notification;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationDao;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.time.LocalDateTime;
import java.util.Optional;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.pms.silverocean.service.insurance.InsuranceModels.*;

@ExtendWith(MockitoExtension.class)
class InsuranceGuestAccessServiceTest {
    @Mock InsuranceGuestAccessRepo accessRepo;
    @Mock InsuranceOperationsService operations;
    @Mock HelpDeskRateLimiter rateLimiter;
    @Spy PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Mock EncryptionService encryptionService;
    @Mock NotificationService notifications;
    @Mock NotificationDao notificationDao;
    @Mock I18NService i18n;
    @Mock UserDao userDao;
    @Mock ConfigService configService;
    @InjectMocks InsuranceGuestAccessService service;

    @BeforeEach void configure() {
        ReflectionTestUtils.setField(service, "otpMinutes", 10L);
        ReflectionTestUtils.setField(service, "accessDays", 30L);
        ReflectionTestUtils.setField(service, "publicUrl", "https://slickhood.com");
    }

    @Test void requestStoresOnlyHashedOtpAndReturnsOpaqueChallenge() {
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Code %s expires %s. Powered by SlickHood.");
        when(accessRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notifications.queueNotification(any())).thenReturn(42L);
        GuestAccessChallenge result = service.requestAccess(
                new GuestAccessRequest("Guest User", " Guest@Example.com ", "+254700000000", "EMAIL"), "127.0.0.1");

        ArgumentCaptor<InsuranceGuestAccess> saved = ArgumentCaptor.forClass(InsuranceGuestAccess.class);
        verify(accessRepo).save(saved.capture());
        assertThat(result.challengeId()).isEqualTo(saved.getValue().getChallengeId());
        assertThat(saved.getValue().getEmail()).isEqualTo("guest@example.com");
        assertThat(saved.getValue().getPhone()).isEqualTo("+254700000000");
        assertThat(saved.getValue().getDeliveryChannel()).isEqualTo("EMAIL");
        assertThat(saved.getValue().getNotificationId()).isEqualTo(42L);
        assertThat(saved.getValue().getOtpHash()).hasSize(60).doesNotContain("000000");
        verify(rateLimiter, times(2)).check(any(), anyInt());
        ArgumentCaptor<NotificationDTO> queued = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notifications).queueNotification(queued.capture());
        assertThat(queued.getValue().notificationType()).isEqualTo(NotificationType.INSURANCE_GUEST_OTP_EMAIL);
        assertThat(queued.getValue().notificationType().getChannel().name()).isEqualTo("EMAIL");
        assertThat(queued.getValue().formattedMessage()).contains("Powered by SlickHood");
    }

    @Test void verifyRejectsExpiredChallengeWithoutIssuingToken() {
        InsuranceGuestAccess access = challenge("123456");
        access.setOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));

        assertThatThrownBy(() -> service.verify(new GuestAccessVerifyRequest("challenge", "123456")))
                .isInstanceOf(PMSCustomException.class);
        assertThat(access.isActive()).isFalse();
        verify(accessRepo).save(access);
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test void requestCanQueueSmsToANormalizedPhoneWhenGatewayIsReady() {
        when(configService.getConfigByName(PMSConfigs.ACTIVE_SMS_PROVIDER)).thenReturn(() -> config("TextSMS"));
        when(configService.getConfigByName(PMSConfigs.TEXT_SMS_PARTNER_ID)).thenReturn(() -> config("partner-1"));
        when(configService.getConfigByName(PMSConfigs.TEXT_SMS_API_KEY)).thenReturn(() -> config("secret-value"));
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Code %s expires in %s minutes. Powered by SlickHood.");
        when(notifications.queueNotification(any())).thenReturn(84L);
        when(accessRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GuestAccessChallenge result = service.requestAccess(
                new GuestAccessRequest("Guest User", "guest@example.com", "0700 000 000", "SMS"), "127.0.0.1");

        ArgumentCaptor<NotificationDTO> queued = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notifications).queueNotification(queued.capture());
        assertThat(queued.getValue().recipient()).isEqualTo("+254700000000");
        assertThat(queued.getValue().notificationType()).isEqualTo(NotificationType.INSURANCE_GUEST_OTP_SMS);
        assertThat(queued.getValue().notificationType().getChannel().name()).isEqualTo("SMS");
        assertThat(queued.getValue().formattedMessage()).contains("Powered by SlickHood");
        assertThat(result.deliveryChannel()).isEqualTo("SMS");
        assertThat(result.maskedDestination()).endsWith("000");
    }

    @Test void deliveryStatusReportsProviderFailureWithoutRevealingTheContact() {
        InsuranceGuestAccess access = challenge("123456");
        access.setDeliveryChannel("EMAIL");
        access.setNotificationId(42L);
        access.setLastSentAt(LocalDateTime.now());
        Notification notification = new Notification();
        notification.setActive(false);
        when(accessRepo.findByChallengeIdAndActiveTrue("challenge")).thenReturn(Optional.of(access));
        when(notificationDao.findById(42L)).thenReturn(Optional.of(notification));

        GuestDeliveryStatus result = service.deliveryStatus(new GuestAccessStatusRequest("challenge"), "127.0.0.1");

        assertThat(result.deliveryStatus()).isEqualTo("FAILED");
        assertThat(result.maskedDestination()).isEqualTo("g***@example.com");
        verify(rateLimiter).check(any(), eq(30));
    }

    @Test void deliveryStatusDistinguishesMailServerAcceptanceFromDelivery() {
        InsuranceGuestAccess access = challenge("123456");
        access.setDeliveryChannel("EMAIL");
        access.setNotificationId(43L);
        access.setLastSentAt(LocalDateTime.now());
        Notification notification = new Notification();
        notification.setActive(true);
        notification.setProviderStatus("ACCEPTED");
        when(accessRepo.findByChallengeIdAndActiveTrue("challenge")).thenReturn(Optional.of(access));
        when(notificationDao.findById(43L)).thenReturn(Optional.of(notification));

        GuestDeliveryStatus result = service.deliveryStatus(new GuestAccessStatusRequest("challenge"), "127.0.0.1");

        assertThat(result.deliveryStatus()).isEqualTo("ACCEPTED");
        assertThat(result.maskedDestination()).isEqualTo("g***@example.com");
    }

    @Test void verifyLocksChallengeAfterFifthIncorrectAttempt() {
        InsuranceGuestAccess access = challenge("123456");
        access.setOtpAttempts(4);
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));

        assertThatThrownBy(() -> service.verify(new GuestAccessVerifyRequest("challenge", "654321")))
                .isInstanceOf(PMSCustomException.class);
        assertThat(access.getOtpAttempts()).isEqualTo(5);
        assertThat(access.isActive()).isFalse();
        verify(accessRepo).save(access);
    }

    @Test void failedVerificationCommitsAttemptAndLockoutState() throws Exception {
        Method verify = InsuranceGuestAccessService.class.getMethod("verify", GuestAccessVerifyRequest.class);
        var attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(verify, InsuranceGuestAccessService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.rollbackOn(new PMSCustomException(org.pms.silverocean.common.ResponseCode.FORBIDDEN_ACCESS)))
                .isFalse();
    }

    @Test void resendIsRejectedDuringServerEnforcedCooldown() {
        InsuranceGuestAccess access = challenge("123456");
        access.setLastSentAt(LocalDateTime.now().minusSeconds(10));
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));

        assertThatThrownBy(() -> service.resend(new GuestAccessResendRequest("challenge", "EMAIL"), "127.0.0.1"))
                .isInstanceOf(PMSCustomException.class);
        verify(rateLimiter, never()).check(any(), anyInt());
        verify(accessRepo, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test void resendIsAllowedAfterCooldown() {
        InsuranceGuestAccess access = challenge("123456");
        access.setDeliveryChannel("EMAIL");
        access.setLastSentAt(LocalDateTime.now().minusSeconds(61));
        access.setSendCount(1);
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Code %s expires %s. Powered by SlickHood.");
        when(notifications.queueNotification(any())).thenReturn(43L);

        GuestAccessChallenge result = service.resend(new GuestAccessResendRequest("challenge", "EMAIL"), "127.0.0.1");

        assertThat(result.deliveryChannel()).isEqualTo("EMAIL");
        assertThat(access.getSendCount()).isEqualTo(2);
        verify(accessRepo).save(access);
    }

    @Test void verifyIssuesThirtyDayOpaqueAccessToken() {
        InsuranceGuestAccess access = challenge("123456");
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));
        when(encryptionService.encrypt(anyString())).thenReturn(new byte[]{1,2,3});
        when(accessRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GuestAccessView result = service.verify(new GuestAccessVerifyRequest("challenge", "123456"));

        assertThat(result.accessToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(access.getAccessTokenHash()).hasSize(64).doesNotContain(result.accessToken());
        assertThat(access.getEncryptedAccessToken()).containsExactly(1,2,3);
        assertThat(access.getAccessExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(access.getVerifiedChannel()).isEqualTo("EMAIL");
    }

    @Test void tokenHashMappingMatchesTheFixedLengthProductionDigestColumn() throws Exception {
        Column mapping = InsuranceGuestAccess.class.getDeclaredField("accessTokenHash").getAnnotation(Column.class);
        assertThat(mapping.columnDefinition()).isEqualTo("CHAR(64)");
        assertThat(mapping.length()).isEqualTo(64);
    }

    private InsuranceGuestAccess challenge(String code) {
        InsuranceGuestAccess access = new InsuranceGuestAccess();
        access.setChallengeId("challenge");
        access.setFullName("Guest User");
        access.setEmail("guest@example.com");
        access.setPhone("+254700000000");
        access.setDeliveryChannel("EMAIL");
        access.setOtpHash(passwordEncoder.encode(code));
        access.setOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        access.setActive(true);
        return access;
    }

    private ConfigDTO config(String value) {
        return new ConfigDTO(1L, "test", value, 0, false);
    }
}
