package org.pms.silverocean.service.auth.totp.impl;

import org.pms.silverocean.database.pms.UserOTPRepo;
import org.pms.silverocean.database.pms.entities.UserOTP;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class OTPDao {
    private final UserOTPRepo userOTPRepo;
    private final AuditLogService auditLogService;

    public OTPDao(UserOTPRepo userOTPRepo, AuditLogService auditLogService) {
        this.userOTPRepo = userOTPRepo;
        this.auditLogService = auditLogService;
    }


    @Transactional
    public void createOTPRecord(Long userId, String contact, OtpType channel, byte[] otp, int otpValiditySeconds) {
        invalidatePreviousOTPByUserID(userId, contact);
        UserOTP userOTP = new UserOTP();
        userOTP.setContact(contact);
        userOTP.setChannel(channel.name());
        userOTP.setOtp(otp);
        userOTP.setAttempts(0);
        userOTP.setOtpExpiryTime(ZonedDateTime.now().plusSeconds(otpValiditySeconds));
        userOTP.setCreatedBy(userId);
        userOTP.setActive(true);
        userOTP.setLastModifiedDate(LocalDateTime.now());
        userOTPRepo.save(userOTP);
        auditLogService.createAuditLog(userOTP, "get_new_otp");
    }

    public Optional<UserOTP> getUsersOTP(long userId) {
        Optional<UserOTP> byCreatedByAndActiveTrue = userOTPRepo.findByCreatedByAndActiveTrue(userId);
        byCreatedByAndActiveTrue.ifPresent(otp -> {
            otp.setAttempts(otp.getAttempts() + 1);
            userOTPRepo.save(otp);
        });
        return byCreatedByAndActiveTrue;
    }

    public Optional<UserOTP> getActiveOTP(long userId) {
        return userOTPRepo.findByCreatedByAndActiveTrue(userId);
    }

    public Optional<UserOTP> getLastVerifiedUserOTP(long userId) {
        return userOTPRepo.findFirstByCreatedByAndActiveFalseAndVerifiedTrueOrderByIdDesc(userId);
    }

    public void deactivateOTPRecord(long userId, String description, boolean verified) {
        userOTPRepo.findByCreatedByAndActiveTrue(userId).ifPresent(otp -> {
            otp.setActive(false);
            otp.setVerified(verified);
            otp.setLastModifiedDate(LocalDateTime.now());
            userOTPRepo.save(otp);
            auditLogService.createAuditLog(otp, "validate_otp", description, verified);
        });

    }

    private void invalidatePreviousOTPByUserID(Long userId, String contact) {
        userOTPRepo.deactivateByCreatedByOrContactAndActiveTrue(userId, contact);
    }
}
