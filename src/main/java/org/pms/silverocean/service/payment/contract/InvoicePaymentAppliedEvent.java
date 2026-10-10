package org.pms.silverocean.service.payment.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Durable fact emitted once for every provider payment uniquely applied to an invoice. */
public record InvoicePaymentAppliedEvent(long invoiceId, String invoiceRef, String providerReference,
                                         BigDecimal appliedAmount, BigDecimal collectedAmount,
                                         BigDecimal outstandingAmount, String currency, LocalDateTime paidAt) {
    public static final String TYPE = "invoice.payment-applied.v1";

    public String dedupeKey() {
        return TYPE + ":" + invoiceId + ":" + providerReference;
    }
}
