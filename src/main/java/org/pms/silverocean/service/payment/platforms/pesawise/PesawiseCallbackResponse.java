package org.pms.silverocean.service.payment.platforms.pesawise;

import org.pms.silverocean.service.payment.PaymentCallBackResponse;

public record PesawiseCallbackResponse(String message) implements PaymentCallBackResponse {
}
