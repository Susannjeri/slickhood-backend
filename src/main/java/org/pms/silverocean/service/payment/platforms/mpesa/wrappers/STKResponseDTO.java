package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * {
 * "MerchantRequestID": "29115-34620561-1",
 * "CheckoutRequestID": "ws_CO_191220191020363925",
 * "ResponseCode": "0",
 * "ResponseDescription": "Success. Request accepted for processing",
 * "CustomerMessage": "Success. Request accepted for processing"
 * }
 */
public record STKResponseDTO(@JsonProperty("MerchantRequestID") String merchantRequestID,
                             @JsonProperty("CheckoutRequestID") String checkoutRequestID,
                             @JsonProperty("ResponseCode") String responseCode,
                             @JsonProperty("ResponseDescription") String responseDescription,
                             @JsonProperty("CustomerMessage") String customerMessage) {
    public String toString() {
        return "{\"MerchantRequestID\": \"" + merchantRequestID + "\", " +
                "\"CheckoutRequestID\": \"" + checkoutRequestID + "\", " +
                "\"ResponseCode\": \"" + responseCode + "\", " +
                "\"ResponseDescription\": \"" + responseDescription + "\", " +
                "\"CustomerMessage\": \"" + customerMessage + "\"}";
    }
}
