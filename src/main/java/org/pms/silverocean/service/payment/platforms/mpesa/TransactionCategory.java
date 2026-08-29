package org.pms.silverocean.service.payment.platforms.mpesa;

import lombok.Getter;

@Getter
public enum TransactionCategory {
    STK("STK", "0"),
    PAYMENT_VALIDATION("Payment Validation", "0"),
    CARD_PAYMENT("Card Payment", "successful"),
    PAYMENT_PROCESSED("Payment Processed", "1"),
    MANUAL_RECORD("Manual Payment", "success");

    private final String name;
    private final String successString;
    TransactionCategory(String name, String successString) {
        this.name = name;
        this.successString = successString;
    }
}
