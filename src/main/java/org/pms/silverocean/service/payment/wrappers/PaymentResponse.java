package org.pms.silverocean.service.payment.wrappers;

import org.pms.silverocean.common.ResponseCode;

public record PaymentResponse (
        boolean success,
        ResponseCode responseCode,
        String body) {
    public PaymentResponse(boolean success, ResponseCode responseCode) {
        this(success, responseCode, null);
    }
}
