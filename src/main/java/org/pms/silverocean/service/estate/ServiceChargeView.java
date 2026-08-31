package org.pms.silverocean.service.estate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record ServiceChargeView(long id, long propertyId, String propertyName, long unitId, String unitRef,
                                long homeownerUserId, long invoiceId, String invoiceRef, BigDecimal amount,
                                String currency, LocalDate dueDate, String description, boolean paid,
                                double pendingAmount, String status, ZonedDateTime createdOn) {
}
