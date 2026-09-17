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
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.helpdesk.HelpDeskRateLimiter;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

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
    @Mock I18NService i18n;
    @Mock UserDao userDao;
    @InjectMocks InsuranceGuestAccessService service;

    @BeforeEach void configure() {
        ReflectionTestUtils.setField(service, "otpMinutes", 10L);
        ReflectionTestUtils.setField(service, "accessDays", 30L);
        ReflectionTestUtils.setField(service, "publicUrl", "https://slickhood.com");
    }

    @Test void requestStoresOnlyHashedOtpAndReturnsOpaqueChallenge() {
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Code %s expires %s");
        when(accessRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        GuestAccessChallenge result = service.requestAccess(
                new GuestAccessRequest("Guest User", " Guest@Example.com ", "+254700000000"), "127.0.0.1");

        ArgumentCaptor<InsuranceGuestAccess> saved = ArgumentCaptor.forClass(InsuranceGuestAccess.class);
        verify(accessRepo).save(saved.capture());
        assertThat(result.challengeId()).isEqualTo(saved.getValue().getChallengeId());
        assertThat(saved.getValue().getEmail()).isEqualTo("guest@example.com");
        assertThat(saved.getValue().getOtpHash()).hasSize(60).doesNotContain("000000");
        verify(rateLimiter, times(2)).check(any(), anyInt());
        verify(notifications).sendNotification(any());
    }

    @Test void verifyRejectsExpiredChallengeWithoutIssuingToken() {
        InsuranceGuestAccess access = challenge("123456");
        access.setOtpExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));

        assertThatThrownBy(() -> service.verify(new GuestAccessVerifyRequest("challenge", "123456")))
                .isInstanceOf(PMSCustomException.class);
        assertThat(access.isActive()).isFalse();
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test void verifyLocksChallengeAfterFifthIncorrectAttempt() {
        InsuranceGuestAccess access = challenge("123456");
        access.setOtpAttempts(4);
        when(accessRepo.findChallengeForUpdate("challenge")).thenReturn(Optional.of(access));

        assertThatThrownBy(() -> service.verify(new GuestAccessVerifyRequest("challenge", "654321")))
                .isInstanceOf(PMSCustomException.class);
        assertThat(access.getOtpAttempts()).isEqualTo(5);
        assertThat(access.isActive()).isFalse();
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
        access.setOtpHash(passwordEncoder.encode(code));
        access.setOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        access.setActive(true);
        return access;
    }
}
