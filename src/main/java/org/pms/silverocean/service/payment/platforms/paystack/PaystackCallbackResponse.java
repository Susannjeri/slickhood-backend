package org.pms.silverocean.service.payment.platforms.paystack;

import org.pms.silverocean.service.payment.PaymentCallBackResponse;

public record PaystackCallbackResponse(String response) implements PaymentCallBackResponse {
}
