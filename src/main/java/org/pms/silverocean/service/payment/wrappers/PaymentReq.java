package org.pms.silverocean.service.payment.wrappers;

public record PaymentReq(
        String invoiceRef,
        long payToUserId,
        long billedUserId,
        double amount,
        String description) {
}
