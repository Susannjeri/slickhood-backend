package org.pms.silverocean.service.auth.totp.impl;


import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

import static org.pms.silverocean.service.auth.totp.impl.EmailOtpServiceImpl.NAME;


@Service(NAME+ TotpServiceFactory.TOTP_SERVICE_SUFFIX)
@Slf4j
public class EmailOtpServiceImpl implements TotpService {
    private final JwtService jwtService;

    private final ConfigService configService;

    private final NotificationService notificationService;
    private final OTPEncryptionService encryptionService;

    private final I18NService i18NService;


    public static final String NAME = "EMAIL";


    public EmailOtpServiceImpl(JwtService jwtService, ConfigService configService, NotificationService notificationService, OTPEncryptionService encryptionService, I18NService i18NService) {
        this.jwtService = jwtService;
        this.configService = configService;
        this.notificationService = notificationService;
        this.encryptionService = encryptionService;
        this.i18NService = i18NService;
    }

    @Override
    public String generateOTPCode(String username) {
        if (!PMSUtils.isValidEmail(username)) {
            throw new PMSCustomException(ResponseCode.EMAIL_OTP_GENERATION_FAILED);
        }
        String secret = PMSUtils.generateRandomOTP();
        encryptionService.saveOTP(username, secret, OtpType.EMAIL, username);
        String formattedMessage = String.format(i18NService.getLocalizedMessage(NotificationType.EMAIL_OTP.getBody()), secret,
                ZonedDateTime.now().plusSeconds(otpValiditySeconds()));

        notificationService.sendNotification(new NotificationDTO(formattedMessage, username, NotificationType.EMAIL_OTP));
        return "Use OTP sent to email";
    }

    @Override
    public boolean validateVerificationToken(String username, String code) {
        try {
            return encryptionService.verifyOTPAgainstValueInDB(username, code, OtpType.EMAIL);
        } catch (Exception e) {
            log.error("An error occurred while validating the TOTP code.", e);
            return false;
        }
    }

    @Override
    public String generateJWT(String username) {
        return jwtService.generateJWT(username);
    }

    int otpValiditySeconds() {
        return configService.getConfigByName(PMSConfigs.OTP_VALIDITY_SECONDS).get().intValue();
    }
}
