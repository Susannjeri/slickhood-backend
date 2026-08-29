package org.pms.silverocean.service.auth.totp;

public interface TotpService {
    String generateOTPCode(String username);

    boolean validateVerificationToken(String username, String code);

    String generateJWT(String username);

}
