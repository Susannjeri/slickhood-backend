package org.pms.silverocean.service.auth.totp.impl;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.QRCodeUtil;
import org.pms.silverocean.service.auth.JwtService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.security.DecryptDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.pms.silverocean.service.auth.totp.impl.GoogleTotpServiceImpl.NAME;

@Slf4j
@Service(NAME+ TotpServiceFactory.TOTP_SERVICE_SUFFIX)
public class GoogleTotpServiceImpl implements TotpService {
    public static final GoogleAuthenticator GOOGLE_AUTHENTICATOR = new GoogleAuthenticator();
    @Value("${spring.application.name}")
    private String SPRING_APPLICATION_NAME;
    private static final int PIXELS_200 = 200;
    public static final String NAME = "GOOGLE_TOTP";

    private final OTPEncryptionService encryptionService;
    private final JwtService jwtService;



    public GoogleTotpServiceImpl(OTPEncryptionService encryptionService, JwtService jwtService) {
        this.encryptionService = encryptionService;
        this.jwtService = jwtService;
    }

    @Override
    public String generateOTPCode(String username) {
        final GoogleAuthenticatorKey key = GOOGLE_AUTHENTICATOR.createCredentials();
        encryptionService.saveOTP(username, key.getKey(), OtpType.GOOGLE_TOTP, OtpType.GOOGLE_TOTP.name());

        String base64QRCodeImage = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(SPRING_APPLICATION_NAME, username, key);

        return QRCodeUtil.generateBase64ImagedQrCode(base64QRCodeImage, PIXELS_200, PIXELS_200);

    }

    @Override
    public boolean validateVerificationToken(String username, String code) {
        try {
            String totpSecret = encryptionService.getDecryptedTotpSecretKey(username);
            if (StringUtils.isNotBlank(totpSecret) && GOOGLE_AUTHENTICATOR.authorize(totpSecret, Integer.parseInt(code))) {
                return true;
            }
            DecryptDTO otpFromDB = encryptionService.getValidOTPFromDB(username);
            if (otpFromDB != null && GOOGLE_AUTHENTICATOR.authorize(otpFromDB.decryptedValue(), Integer.parseInt(code))) {
                encryptionService.clearUsedOTP(username, OtpType.GOOGLE_TOTP, otpFromDB.decryptedValue());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("An error occurred while validating the TOTP code.", e);
            return false;
        }
    }

    @Override
    public String generateJWT(String username) {
        return jwtService.generateJWT(username);
    }

}
