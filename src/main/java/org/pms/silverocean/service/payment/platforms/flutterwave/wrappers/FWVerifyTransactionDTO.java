package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FWVerifyTransactionDTO(
        String status,
        String message,
        FWChargeCompletedData data
) {
    @Override
    public String toString() {
        return "{" +
                "\"status\":\"" + status + "\"," +
                "\"message\":\"" + message + "\"," +
                "\"data\":" + data +
                "}";
    }
}
