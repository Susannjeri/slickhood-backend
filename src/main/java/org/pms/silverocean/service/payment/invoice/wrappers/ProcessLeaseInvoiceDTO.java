package org.pms.silverocean.service.payment.invoice.wrappers;

import java.time.LocalDate;

public record ProcessLeaseInvoiceDTO(Long id,
                                     String leaseMode,
                                     LocalDate leaseDate,
                                     LocalDate nextPaymentDate,
                                     Double price,
                                     String currency,
                                     boolean isCharges,
                                     long unitId,
                                     long tenantUserId) {
}
