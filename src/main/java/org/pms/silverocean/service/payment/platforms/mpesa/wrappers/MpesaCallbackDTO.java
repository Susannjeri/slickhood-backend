package org.pms.silverocean.service.payment.platforms.mpesa.wrappers;

import org.pms.silverocean.service.payment.PaymentCallBackRequest;

public record MpesaCallbackDTO(MPesaPaymentDTO mpesaPaymentDTO, STKCallbackResponse stkCallbackResponse,
                               Long userId, String sourceIp,
                               MpesaCallBackType mpesaCallBackType) implements PaymentCallBackRequest {
}
