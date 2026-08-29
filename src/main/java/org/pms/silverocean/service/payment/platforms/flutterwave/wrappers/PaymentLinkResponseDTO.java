package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentLinkResponseDTO(
        String status,
        String message,
        FWData data
) {
    @Override
    public String toString() {
        return "{\"status\": \"" + status + "\", " +
                "\"message\": \"" + message + "\", " +
                "\"data\": {\"link\":\"hidden\", \"id\": " + data.id() + "}" +
                "}";
    }
}
