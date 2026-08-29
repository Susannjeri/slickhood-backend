package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.platforms.mpesa.MPesaResultCodes;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MPesaPaymentResponseDTO(
        @JsonProperty("ResultCode")
        String resultCode,
        @JsonProperty("ResultDesc")
        String resultDesc
) implements PaymentCallBackResponse {
    public  MPesaPaymentResponseDTO(MPesaResultCodes resultCode) {
        this(resultCode.getCode(), resultCode.getDesc());
    }

    public String toString() {
            return "{\"ResultCode\": \"" + resultCode + "\", " +
                    "\"ResultDesc\": \"" + resultDesc + "\"}";
    }
}
