package org.pms.silverocean.controller.wrappers;

public record LoginResponseDTO (
     boolean totpEnabled,
     boolean mfaSetup, String jwt, String refreshToken) {
}
