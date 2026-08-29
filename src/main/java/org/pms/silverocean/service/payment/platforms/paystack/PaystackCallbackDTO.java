package org.pms.silverocean.service.payment.platforms.paystack;

import org.pms.silverocean.service.payment.PaymentCallBackRequest;

public record PaystackCallbackDTO(String rawBody, String sourceIp) implements PaymentCallBackRequest {
}
