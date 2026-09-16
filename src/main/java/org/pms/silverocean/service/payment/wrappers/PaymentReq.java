package org.pms.silverocean.service.payment.wrappers;

import java.math.BigDecimal;

public record PaymentReq(
        String invoiceRef,
        long payToUserId,
        long billedUserId,
        BigDecimal amount,
        String description) {
    public PaymentReq(String invoiceRef, long payToUserId, long billedUserId, double amount, String description) {
        this(invoiceRef, payToUserId, billedUserId, BigDecimal.valueOf(amount), description);
    }
}
