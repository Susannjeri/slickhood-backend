package org.pms.silverocean.service.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record SaleView(long id, long propertyId, String propertyName, Long unitId, String unitRef,
                       long salesAgentUserId, String salesAgentName, Long buyerUserId, String buyerName,
                       String buyerEmail, SaleStatus status, BigDecimal askingPrice, BigDecimal offerAmount,
                       String currency, LocalDateTime offerAcceptedAt, LocalDateTime completedAt,
                       String notes, ZonedDateTime createdOn) {
    public SaleView redactInternalNotes() {
        return new SaleView(id, propertyId, propertyName, unitId, unitRef, salesAgentUserId, salesAgentName,
                buyerUserId, buyerName, buyerEmail, status, askingPrice, offerAmount, currency,
                offerAcceptedAt, completedAt, null, createdOn);
    }
}
