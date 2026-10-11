package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;
import java.math.BigDecimal;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "pms_sp_booking", indexes = {
        @Index(name = "idx_sp_booking_serviceId", columnList = "serviceId"),
        @Index(name = "idx_sp_booking_createdBy", columnList = "createdBy"),
        @Index(name = "idx_sp_booking_status", columnList = "status")
}, uniqueConstraints = @UniqueConstraint(name = "ux_sp_booking_customer_request",
        columnNames = {"created_by", "client_request_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ServiceBooking extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    @jakarta.persistence.Column(length = 120)
    private String serviceTitleSnapshot;
    @jakarta.persistence.Column(length = 255)
    private String serviceCategorySnapshot;
    @jakarta.persistence.Column(length = 2000)
    private String serviceDescriptionSnapshot;
    @jakarta.persistence.Column(length = 255)
    private String providerBusinessNameSnapshot;
    @jakarta.persistence.Column(length = 100)
    private String clientRequestId;
    private ZonedDateTime scheduledAt;
    private ZonedDateTime completedAt;
    private String status;
    @jakarta.persistence.Column(length = 2000)
    private String notes;
    @jakarta.persistence.Column(length = 1000)
    private String cancellationReason;
    private BigDecimal quotedAmount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PricingUnit pricingUnit;
    private Long propertyId;
    private Long unitId;
    @jakarta.persistence.Column(length = 500)
    private String serviceAddress;
    private Long paymentAccountId;
    private String paymentChannel;
    private String invoiceRef;
    private String paymentStatus;
    private String providerReference;
    private String refundStatus;
    private String refundReference;
    private BigDecimal refundedAmount;
    private String settlementStatus;
    private String settlementReference;
    private BigDecimal settledAmount;
    @jakarta.persistence.Column(length = 500)
    private String completionEvidenceReference;
    private ZonedDateTime startedAt;
    private ZonedDateTime disputeDeadlineAt;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"serviceId\":" + serviceId + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"scheduledAt\":\"" + scheduledAt + "\"," +
                "\"status\":\"" + status + "\"" +
                "}";
    }
}
