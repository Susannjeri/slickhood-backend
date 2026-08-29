package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.database.pms.entities.PMSInvoice;

import java.time.ZonedDateTime;

public record SubscriptionBillingItemDTO(
        String invoiceRef,
        ZonedDateTime createdOn,
        String planCode,
        String currency,
        double amount,
        double pendingAmount,
        String status
) {
    public static SubscriptionBillingItemDTO from(PMSInvoice invoice) {
        String status = invoice.isPaid()
                ? "SUCCESSFUL"
                : invoice.isTransactionInProgress() ? "PROCESSING" : "PENDING";
        return new SubscriptionBillingItemDTO(invoice.getRef(), invoice.getCreatedOn(),
                invoice.getSubscriptionPlanCode(), invoice.getCurrency(), invoice.getAmount(),
                invoice.getPendingAmount(), status);
    }
}
