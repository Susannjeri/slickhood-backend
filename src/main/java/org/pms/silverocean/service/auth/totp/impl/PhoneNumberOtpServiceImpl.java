package org.pms.silverocean.service.auth.totp.impl;

import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

import static org.pms.silverocean.service.auth.totp.impl.PhoneNumberOtpServiceImpl.NAME;

@Service(NAME + TotpServiceFactory.TOTP_SERVICE_SUFFIX)
public class PhoneNumberOtpServiceImpl extends EmailOtpServiceImpl {
    private final OTPEncryptionService encryptionService;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final UserDao userDao;

    public static final String NAME = "SMS";

    public PhoneNumberOtpServiceImpl(JwtService jwtService, ConfigService configService, NotificationService notificationService, OTPEncryptionService encryptionService, I18NService i18NService, UserDao userDao) {
        super(jwtService, configService, notificationService, encryptionService, i18NService);
        this.encryptionService = encryptionService;
        this.notificationService = notificationService;
        this.i18NService = i18NService;
        this.userDao = userDao;
    }

    @Override
    public String generateOTPCode(String username) {
        String phoneNumber = userDao.findByEmail(username)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS))
                .getPhoneNumber();
        if (StringUtils.isBlank(phoneNumber)) {
            throw new PMSCustomException(ResponseCode.PHONENUMBER_IS_MISSING);
        }
        String secret = PMSUtils.generateRandomOTP();
        encryptionService.saveOTP(username, secret, OtpType.SMS, phoneNumber);
        String formattedMessage = String.format(i18NService.getLocalizedMessage(NotificationType.OTP_SMS.getBody()), secret,
                ZonedDateTime.now().plusSeconds(otpValiditySeconds()));
        notificationService.sendNotification(new NotificationDTO(formattedMessage, phoneNumber, NotificationType.OTP_SMS));
        return "Use OTP sent to phone";
    }
}
