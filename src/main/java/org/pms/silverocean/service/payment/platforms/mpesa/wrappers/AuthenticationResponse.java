package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") String expiresIn) {
}
