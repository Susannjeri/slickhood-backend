package org.pms.silverocean.controller.wrappers;

public record LoginResponseDTO (
     boolean totpEnabled,
     boolean mfaSetup, String jwt, String refreshToken, String refreshRequestId) {

    public LoginResponseDTO(boolean totpEnabled, boolean mfaSetup, String jwt, String refreshToken) {
        this(totpEnabled, mfaSetup, jwt, refreshToken, null);
    }
}
