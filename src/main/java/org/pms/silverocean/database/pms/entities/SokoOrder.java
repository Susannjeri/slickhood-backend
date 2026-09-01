package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_soko_order", indexes = {
        @Index(name = "idx_soko_order_customer", columnList = "customerUserId,createdOn"),
        @Index(name = "idx_soko_order_store", columnList = "storeId,status"),
        @Index(name = "idx_soko_order_invoice", columnList = "invoiceRef", unique = true)
})
@Getter @Setter @NoArgsConstructor
public class SokoOrder extends BaseCreatorEntity {
    private String orderNumber;
    private long storeId;
    private long customerUserId;
    private String status;
    private String paymentStatus;
    private String invoiceRef;
    private String deliveryMethod;
    private String deliveryAddress;
    private String customerPhone;
    private String notes;
    private Long destinationUnitId;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private String currency;
    private ZonedDateTime placedAt;
    private ZonedDateTime confirmedAt;
    private ZonedDateTime dispatchedAt;
    private ZonedDateTime completedAt;
    private Long deliveryVisitorId;
    private Long riderId;
    private String courierName;
    private String courierPhone;
    private String courierVehiclePlate;
    @JsonIgnore
    private String deliveryCode;
    @JsonIgnore
    private byte[] encryptedDeliveryCode;
    @JsonIgnore
    private String checkoutIdempotencyKey;
    private boolean deliveryCodeVerified;
    private int deliveryCodeAttempts;
    private ZonedDateTime reservationExpiresAt;
    private boolean stockReleased;
    private ZonedDateTime cancelledAt;
    private String cancellationReason;
    private String refundStatus;
    private String refundReference;
    private BigDecimal refundedAmount;
    private String settlementStatus;
    private String settlementReference;
    private BigDecimal settledAmount;
    private String deliveryRecipientName;
    @JsonIgnore
    private String deliveryProofReference;
    private ZonedDateTime deliveryProofAt;
    private ZonedDateTime expectedArrivalAt;
    private String deliveryProofContentType;
    private Long deliveryProofSize;
}
