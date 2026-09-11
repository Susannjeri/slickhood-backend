package org.pms.silverocean.service.payment.platforms.pesawise;

import org.pms.silverocean.service.payment.PaymentCallBackRequest;

public record PesawiseCallbackDTO(String rawBody, String secretHash, String eventType, String sourceIp)
        implements PaymentCallBackRequest {
}
