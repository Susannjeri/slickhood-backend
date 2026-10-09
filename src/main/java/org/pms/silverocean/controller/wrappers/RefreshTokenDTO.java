package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenDTO(
        @NotBlank String refreshToken,
        @Size(min = 32, max = 128) String requestId) {

    public RefreshTokenDTO(String refreshToken) {
        this(refreshToken, null);
    }
}
