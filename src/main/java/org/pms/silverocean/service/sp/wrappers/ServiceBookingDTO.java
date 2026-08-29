package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ServiceBooking;

import java.time.ZonedDateTime;
import java.math.BigDecimal;
import org.pms.silverocean.service.sp.enums.PricingUnit;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceBookingDTO(
    long id, long serviceId, long bookedByUserId, ZonedDateTime scheduledAt,
    ZonedDateTime completedAt, String status, String notes, String cancellationReason, ZonedDateTime createdOn,
    String serviceName, String serviceProviderName, String bookedByUserName,
    BigDecimal quotedAmount, String currency, PricingUnit pricingUnit,
    Long propertyId, Long unitId, Long paymentAccountId, String paymentChannel,
    String invoiceRef, String paymentStatus, String providerReference,
    String refundStatus, String refundReference, BigDecimal refundedAmount,
    String settlementStatus, String settlementReference, BigDecimal settledAmount,
    String completionEvidenceReference, ZonedDateTime startedAt
) {
    public ServiceBookingDTO(ServiceBooking b, String serviceName, String serviceProviderName, String bookedByUserName) {
        this(b.getId(), b.getServiceId(), b.getCreatedBy(), b.getScheduledAt(),
             b.getCompletedAt(), b.getStatus(), b.getNotes(), b.getCancellationReason(), b.getCreatedOn(),
             serviceName, serviceProviderName, bookedByUserName,
             b.getQuotedAmount(), b.getCurrency(), b.getPricingUnit(),
             b.getPropertyId(), b.getUnitId(), b.getPaymentAccountId(), b.getPaymentChannel(),
             b.getInvoiceRef(), b.getPaymentStatus(), b.getProviderReference(),
             b.getRefundStatus(), b.getRefundReference(), b.getRefundedAmount(),
             b.getSettlementStatus(), b.getSettlementReference(), b.getSettledAmount(),
             b.getCompletionEvidenceReference(), b.getStartedAt());
    }
}
