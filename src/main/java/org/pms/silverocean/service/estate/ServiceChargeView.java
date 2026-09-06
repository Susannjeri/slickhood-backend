package org.pms.silverocean.service.estate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record ServiceChargeView(long id, long propertyId, String propertyName, long unitId, String unitRef,
                                long homeownerUserId, long invoiceId, String invoiceRef, BigDecimal amount,
                                String currency, LocalDate dueDate, String description, boolean paid,
                                double pendingAmount, String status, ZonedDateTime createdOn) {
    public ServiceChargeView {
        // Database CURRENT_DATE may still be yesterday during Nairobi's first three hours.
        // All estate screens use the same business day as reminder/billing services.
        status = statusOn(paid, dueDate, LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId()));
    }

    static String statusOn(boolean paid, LocalDate dueDate, LocalDate today) {
        return paid ? "PAID" : dueDate != null && dueDate.isBefore(today) ? "OVERDUE" : "DUE";
    }
}
