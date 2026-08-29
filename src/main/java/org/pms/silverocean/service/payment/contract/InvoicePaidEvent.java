package org.pms.silverocean.service.payment.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoicePaidEvent(long invoiceId,String invoiceRef,String providerReference,
                               BigDecimal paidAmount,String currency,LocalDateTime paidAt) {
    public static final String TYPE="invoice.paid.v1";
    public String dedupeKey(){return TYPE+":"+invoiceId;}
}
