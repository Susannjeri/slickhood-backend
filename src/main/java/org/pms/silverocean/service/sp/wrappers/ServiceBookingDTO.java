package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.ComplaintCase;

import java.time.ZonedDateTime;
import java.math.BigDecimal;
import org.pms.silverocean.service.sp.enums.PricingUnit;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceBookingDTO(
    long id, long serviceId, long bookedByUserId, ZonedDateTime scheduledAt, ZonedDateTime scheduledEndAt,
    ZonedDateTime completedAt, String status, String notes, String cancellationReason, ZonedDateTime createdOn,
    String serviceName, String serviceProviderName, String bookedByUserName,
    BigDecimal quotedAmount, String currency, PricingUnit pricingUnit,
    BigDecimal pricingUnitRate, BigDecimal unitQuantity,
    Long propertyId, Long unitId, String serviceAddress, Long paymentAccountId, String paymentChannel,
    String invoiceRef, String paymentStatus, String providerReference,
    String refundStatus, String refundReference, BigDecimal refundedAmount,
    String settlementStatus, String settlementReference, BigDecimal settledAmount,
    String completionEvidenceReference, ZonedDateTime startedAt, ZonedDateTime disputeDeadlineAt,
    boolean customerBooking, boolean rated,
    Long complaintId, String complaintStatus, String complaintResolution
) {
    public ServiceBookingDTO(ServiceBooking b, String serviceName, String serviceProviderName, String bookedByUserName, long viewerUserId) {
        this(b.getId(), b.getServiceId(), b.getCreatedBy(), b.getScheduledAt(), b.getScheduledEndAt(),
             b.getCompletedAt(), b.getStatus(), b.getNotes(), b.getCancellationReason(), b.getCreatedOn(),
             serviceName, serviceProviderName, bookedByUserName,
             b.getQuotedAmount(), b.getCurrency(), b.getPricingUnit(), b.getPricingUnitRate(), b.getUnitQuantity(),
             b.getPropertyId(), b.getUnitId(), b.getServiceAddress(), b.getPaymentAccountId(), b.getPaymentChannel(),
             b.getInvoiceRef(), b.getPaymentStatus(), b.getProviderReference(),
             b.getRefundStatus(), b.getRefundReference(), b.getRefundedAmount(),
             b.getSettlementStatus(), b.getSettlementReference(), b.getSettledAmount(),
             b.getCompletionEvidenceReference(), b.getStartedAt(), b.getDisputeDeadlineAt(),
             java.util.Objects.equals(b.getCreatedBy(), viewerUserId), false, null, null, null);
    }

    public ServiceBookingDTO withRated(boolean rated) {
        return new ServiceBookingDTO(id, serviceId, bookedByUserId, scheduledAt, scheduledEndAt, completedAt, status,
                notes, cancellationReason, createdOn, serviceName, serviceProviderName, bookedByUserName,
                quotedAmount, currency, pricingUnit, pricingUnitRate, unitQuantity, propertyId, unitId, serviceAddress, paymentAccountId, paymentChannel,
                invoiceRef, paymentStatus, providerReference, refundStatus, refundReference, refundedAmount,
                settlementStatus, settlementReference, settledAmount, completionEvidenceReference, startedAt,
                disputeDeadlineAt, customerBooking, rated, complaintId, complaintStatus, complaintResolution);
    }

    public ServiceBookingDTO withDispute(ComplaintCase complaint) {
        return new ServiceBookingDTO(id, serviceId, bookedByUserId, scheduledAt, scheduledEndAt, completedAt, status,
                notes, cancellationReason, createdOn, serviceName, serviceProviderName, bookedByUserName,
                quotedAmount, currency, pricingUnit, pricingUnitRate, unitQuantity, propertyId, unitId, serviceAddress, paymentAccountId, paymentChannel,
                invoiceRef, paymentStatus, providerReference, refundStatus, refundReference, refundedAmount,
                settlementStatus, settlementReference, settledAmount, completionEvidenceReference, startedAt,
                disputeDeadlineAt, customerBooking, rated,
                complaint == null ? null : complaint.getId(),
                complaint == null ? null : complaint.getStatus(),
                complaint == null ? null : org.apache.commons.lang3.StringUtils.defaultIfBlank(
                        complaint.getPublicResolution(), complaint.getResolution()));
    }
}
