package org.pms.silverocean.service.payment.invoice.wrappers;

import java.time.ZonedDateTime;
import java.time.LocalDate;

public record InvoiceDTO(long id, ZonedDateTime createdOn, String propertyDetails, Long propertyId,
                         String propertyName, String unitRef, String tenantName, String ref,
                         String currency, double amount, double pendingAmount, boolean paid,
                         Long paymentAccountId, String billingType, LocalDate dueDate,
                         String issuerName, String issuerType, String issuerLogoUrl) {
}
