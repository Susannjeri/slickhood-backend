package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import org.pms.silverocean.service.payment.PaymentCallBackRequest;

public record PesalinkCallbackDTO(IPNCallbackDTO ipnCallbackDTO, PesalinkValidatePaymentRequestDTO pesalinkValidatePaymentRequestDTO,
                                  String ip, String signature, PesalinkCallbackType pesalinkCallbackType) implements PaymentCallBackRequest {
}
