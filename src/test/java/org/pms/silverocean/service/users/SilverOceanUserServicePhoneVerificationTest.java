package org.pms.silverocean.service.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.UserOTP;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.impl.OTPEncryptionService;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SilverOceanUserServicePhoneVerificationTest {

    @Mock private I18NService i18NService;
    @Mock private UserDao userDao;
    @Mock private NotificationService notificationService;
    @Mock private OTPEncryptionService otpEncryptionService;

    private SilverOceanUserService service;
    private Users currentUser;

    @BeforeEach
    void setUp() {
        service = new SilverOceanUserService(i18NService, userDao, notificationService, otpEncryptionService);
        ReflectionTestUtils.setField(service, "otpResendCooldownSeconds", 60);
        currentUser = new Users();
        currentUser.setId(41L);
        currentUser.setEmail("owner@example.com");
        when(userDao.getUserObject()).thenReturn(currentUser);
    }

    @Test
    void permitsVerificationWhenNumberAlreadyBelongsToCurrentUser() {
        when(userDao.findByPhone("+254111379961")).thenReturn(Optional.of(currentUser));
        when(otpEncryptionService.getActiveOTP(41L)).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Code %s");

        service.saveChangeContactRequestAndSendOTP("+254111379961", NotificationChannel.SMS);

        verify(otpEncryptionService).saveOTP(eq("owner@example.com"), anyString(), eq(OtpType.SMS), eq("+254111379961"));
        verify(notificationService).sendNotification(any());
    }

    @Test
    void rejectsNumberOwnedByAnotherUserWithSpecificResponse() {
        Users anotherUser = new Users();
        anotherUser.setId(99L);
        when(userDao.findByPhone("+254111379961")).thenReturn(Optional.of(anotherUser));

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> service.saveChangeContactRequestAndSendOTP("+254111379961", NotificationChannel.SMS));

        assertEquals(ResponseCode.PHONE_NUMBER_ALREADY_IN_USE, exception.getResponseCode());
        verify(otpEncryptionService, never()).saveOTP(anyString(), anyString(), any(), anyString());
    }

    @Test
    void rateLimitsRepeatedCodeRequestsForSameNumber() {
        UserOTP activeOtp = new UserOTP();
        activeOtp.setContact("+254111379961");
        activeOtp.setChannel(OtpType.SMS.name());
        activeOtp.setCreatedOn(ZonedDateTime.now());
        when(userDao.findByPhone("+254111379961")).thenReturn(Optional.empty());
        when(otpEncryptionService.getActiveOTP(41L)).thenReturn(Optional.of(activeOtp));

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> service.saveChangeContactRequestAndSendOTP("+254111379961", NotificationChannel.SMS));

        assertEquals(ResponseCode.OTP_RESEND_TOO_SOON, exception.getResponseCode());
    }

    @Test
    void completesPhoneOtpUsingSmsRatherThanEmailPath() {
        UserOTP activeOtp = new UserOTP();
        activeOtp.setChannel(OtpType.SMS.name());
        UserOTP verifiedOtp = new UserOTP();
        verifiedOtp.setChannel(NotificationChannel.SMS.name());
        verifiedOtp.setContact("+254111379961");
        when(otpEncryptionService.getActiveOTP(41L)).thenReturn(Optional.of(activeOtp));
        when(otpEncryptionService.verifyOTPAgainstValueInDB("owner@example.com", "ABC123", OtpType.SMS)).thenReturn(true);
        when(otpEncryptionService.getLastVerifiedOTP(41L)).thenReturn(verifiedOtp);

        service.verifyOTPAndUpdateContact("ABC123");

        assertEquals("+254111379961", currentUser.getPhoneNumber());
        assertTrue(currentUser.isPhoneVerified());
        verify(userDao).save(currentUser);
    }
}
