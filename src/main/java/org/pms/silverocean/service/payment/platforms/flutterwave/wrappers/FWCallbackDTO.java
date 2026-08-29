package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import org.pms.silverocean.service.payment.PaymentCallBackRequest;

public record FWCallbackDTO<T>(FWWebhookDTO<T> fwWebhookDTO, String sourceIp) implements PaymentCallBackRequest {
}
