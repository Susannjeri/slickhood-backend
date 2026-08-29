package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PesalinkValidatePaymentResponseDTO(String billRef, String amount, String billId, String status, String statusDescription) implements PaymentCallBackResponse {
    public PesalinkValidatePaymentResponseDTO(PesalinkValidatePaymentRequestDTO request, String transId, PesalinkStatus status) {
        this(request.billRef(), String.valueOf(request.amount()), transId, status.getStatus(), status.getDescription());
    }
}
