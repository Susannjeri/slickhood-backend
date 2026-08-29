package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonInclude(JsonInclude.Include.NON_NULL) @JsonIgnoreProperties(ignoreUnknown = true)
public record STKCallback(@JsonProperty("MerchantRequestID") String merchantRequestID,
                          @JsonProperty("CheckoutRequestID") String checkoutRequestID,
                          @JsonProperty("ResultCode") int resultCode,
                          @JsonProperty("ResultDesc") String resultDesc,
                          @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata) {
}
