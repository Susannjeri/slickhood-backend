package org.pms.silverocean.service.auth.totp.impl;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.UserOTP;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.security.DecryptDTO;
import org.pms.silverocean.service.security.EncryptionService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.pms.silverocean.common.ResponseCode.COULD_NOT_LOAD_CONTACT_CHANGE_REQUEST;
import static org.pms.silverocean.common.ResponseCode.LOAD_USER_ERROR;
import static org.pms.silverocean.common.ResponseCode.VERIFY_EMAIL_TO_SETUP_AUTHENTICATOR;

@Service
public class OTPEncryptionService {
    private final EncryptionService encryptionService;
    private final ConfigService configService;
    private final UserDao userDao;
    private final OTPDao otpDao;

    public OTPEncryptionService(EncryptionService encryptionService, ConfigService configService, UserDao userDao, OTPDao otpDao) {
        this.encryptionService = encryptionService;
        this.configService = configService;
        this.userDao = userDao;
        this.otpDao = otpDao;
    }

    public void saveOTP(String username, String secret, OtpType verificationOption, String contact) {
        Optional<Users> userByMail = userDao.findByEmail(username);
        if (userByMail.isPresent()) {
            Users user = userByMail.get();
            if (OtpType.GOOGLE_TOTP.equals(verificationOption) && !user.isEmailVerified()) {
                //Due to the chances that a user might use local register with an invalid email, then use google authenticator as the verification method, emailVerified needs to be checked
                throw new PMSCustomException(VERIFY_EMAIL_TO_SETUP_AUTHENTICATOR);
            }
            otpDao.createOTPRecord(user.getId(), contact, verificationOption, encryptionService.encrypt(secret), configService.getConfigByName(PMSConfigs.OTP_VALIDITY_SECONDS).get().intValue());
        } else {
            throw new PMSCustomException(LOAD_USER_ERROR);
        }
    }

    public UserOTP getLastVerifiedOTP(long userId) {
       return otpDao.getLastVerifiedUserOTP(userId).orElseThrow(() -> new PMSCustomException(COULD_NOT_LOAD_CONTACT_CHANGE_REQUEST));
    }

    public Optional<UserOTP> getActiveOTP(long userId) {
        return otpDao.getActiveOTP(userId);
    }

    public boolean verifyOTPAgainstValueInDB(String username, String code, OtpType otpType) {
        Optional<Users> userByMail = userDao.findByEmail(username);
        if (userByMail.isPresent()) {
            Users users = userByMail.get();
            UserOTP userOTP = otpDao.getUsersOTP(users.getId()).orElseThrow(() -> new PMSCustomException(ResponseCode.TOTP_VALIDATION_FAILURE));

            DecryptDTO otp = encryptionService.decrypt(userOTP.getOtp());

            if (ZonedDateTime.now().isAfter(userOTP.getOtpExpiryTime())) {
                updateOTPRecord(username, "OTP Expired");
                return false;
            }
            if (userOTP.getAttempts() > configService.getConfigByName(PMSConfigs.MAX_OTP_ATTEMPTS).get().intValue()) {
                updateOTPRecord(username, "Max Attempts Exceeded");
                return false;
            }
            if (StringUtils.isNotBlank(otp.decryptedValue()) && otp.decryptedValue().equals(String.valueOf(code))) {
                clearUsedOTP(username, otpType, otp.decryptedValue());
                return true;
            }
            return false;
        } else {
            throw new PMSCustomException(LOAD_USER_ERROR);
        }
    }

    public DecryptDTO getValidOTPFromDB(String username) {
        Optional<Users> userByMail = userDao.findByEmail(username);
        if (userByMail.isPresent()) {
            Users users = userByMail.get();
            UserOTP userOTP = otpDao.getUsersOTP(users.getId()).orElseThrow(() -> new PMSCustomException(ResponseCode.TOTP_VALIDATION_FAILURE));

            DecryptDTO otp = encryptionService.decrypt(userOTP.getOtp());

            if (ZonedDateTime.now().isAfter(userOTP.getOtpExpiryTime())) {
                updateOTPRecord(username, "OTP Expired");
                return null;
            }
            if (userOTP.getAttempts() > configService.getConfigByName(PMSConfigs.MAX_OTP_ATTEMPTS).get().intValue()) {
                updateOTPRecord(username, "Max Attempts Exceeded");
                return null;
            }
            return otp;
        } else {
            throw new PMSCustomException(LOAD_USER_ERROR);
        }
    }



    public String getDecryptedTotpSecretKey(String username) {
        Optional<Users> userByMail = userDao.findByEmail(username);
        if (userByMail.isPresent()) {
            Users users = userByMail.get();
            DecryptDTO totpSecret = encryptionService.decrypt(users.getTotpSecret());

            return totpSecret == null ? null : totpSecret.decryptedValue();
        } else {
            throw new PMSCustomException(LOAD_USER_ERROR);
        }
    }


    private void updateOTPRecord(String userName, String description) {
        Users userByMail = userDao.findByEmail(userName).orElseThrow(() -> new PMSCustomException(LOAD_USER_ERROR));
        otpDao.deactivateOTPRecord(userByMail.getId(), description, false);
    }

    public void clearUsedOTP(String userName, OtpType verificationOption, String otp) {
        try {
            Optional<Users> userByMail = userDao.findByEmail(userName);
            if (userByMail.isPresent()) {
                Users user = userByMail.get();

                user.setActive(true);
                if (OtpType.EMAIL.equals(verificationOption)) {
                    user.setEmailVerified(true);
                    if (AccountStatus.PENDING_EMAIL_VERIFICATION.name().equals(user.getAccountStatus())) {
                        user.setAccountStatus(AccountStatus.PENDING_KYC.name());
                    }
                } else if (OtpType.GOOGLE_TOTP.equals(verificationOption)) {
                    user.setTotpSecret(encryptionService.encrypt(otp));
                }
                userDao.save(user);
                otpDao.deactivateOTPRecord(user.getId(), "OTP Verified Successfully", true);
            } else {
                throw new PMSCustomException(LOAD_USER_ERROR);
            }
        } catch (Exception e) {
            throw new PMSCustomException(LOAD_USER_ERROR, e);
        }
    }
}
