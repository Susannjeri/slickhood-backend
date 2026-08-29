package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import org.pms.silverocean.service.payment.PaymentCallBackResponse;

public record FWCallbackResponse(String response) implements PaymentCallBackResponse {
}
