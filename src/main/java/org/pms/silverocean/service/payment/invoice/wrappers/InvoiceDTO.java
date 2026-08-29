package org.pms.silverocean.service.payment.invoice.wrappers;

import java.time.ZonedDateTime;

public record InvoiceDTO(long id, ZonedDateTime createdOn, String propertyDetails, Long propertyId, String tenantName, String ref, String currency, double amount, boolean paid, Long paymentAccountId) {
}
