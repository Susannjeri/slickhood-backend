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
        @Index(name = "idx_soko_order_buyer_store_status", columnList = "customerUserId,storeId,status,active"),
        @Index(name = "uk_soko_checkout_idempotency", columnList = "customerUserId,checkoutIdempotencyKey", unique = true),
        @Index(name = "idx_soko_order_invoice", columnList = "invoiceRef", unique = true)
})
@Getter @Setter @NoArgsConstructor
public class SokoOrder extends BaseCreatorEntity implements Auditable {
    private String orderNumber;
    private long storeId;
    private long customerUserId;
    private String status;
    private String paymentStatus;
    private String invoiceRef;
    /** Receiving destination frozen at checkout; later shop edits must not reroute an existing order. */
    private Long paymentAccountId;
    private String paymentChannel;
    /** Shop identity and collection point frozen at checkout for reliable pickup and delivery fulfilment. */
    private String storeNameSnapshot;
    private String storeAddressSnapshot;
    private String storePhoneSnapshot;
    private Double storeLatitudeSnapshot;
    private Double storeLongitudeSnapshot;
    private String deliveryMethod;
    private String deliveryAddress;
    /** Customer-selected delivery pin frozen at checkout for radius enforcement and fulfilment. */
    private Double deliveryLatitude;
    private Double deliveryLongitude;
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
    /** Cumulative refund target currently being worked by Finance; confirmed refunds remain in refundedAmount. */
    private BigDecimal refundRequestedAmount;
    /** Provider reversals and chargebacks are independent of customer refunds and must never overwrite them. */
    private String reversalStatus;
    private String reversalReference;
    private BigDecimal reversedAmount;
    private String chargebackStatus;
    private String chargebackReference;
    private BigDecimal chargedBackAmount;
    private String settlementStatus;
    private String settlementReference;
    private BigDecimal settledAmount;
    private String deliveryRecipientName;
    @JsonIgnore
    private String deliveryProofReference;
    private ZonedDateTime deliveryProofAt;
    private ZonedDateTime expectedArrivalAt;
    private ZonedDateTime assignedAt;
    private ZonedDateTime assignmentAcceptedAt;
    /**
     * SHA-256 digest of the opaque rider-assignment bearer. The raw bearer is
     * delivered once by SMS and is never persisted or returned to the merchant.
     */
    @JsonIgnore
    @jakarta.persistence.Column(length=64)
    private String riderAssignmentTokenHash;
    private ZonedDateTime riderAssignmentTokenIssuedAt;
    private ZonedDateTime riderAssignmentTokenExpiresAt;
    private ZonedDateTime riderAssignmentTokenRevokedAt;
    private ZonedDateTime riderAssignmentLinkRequestedAt;
    private int riderAssignmentLinkRequestCount;
    private ZonedDateTime riderAssignmentDeclinedAt;
    @jakarta.persistence.Column(length=500)
    private String riderAssignmentDeclineReason;
    private ZonedDateTime collectedAt;
    private ZonedDateTime deliveryFailedAt;
    private ZonedDateTime returnedAt;
    @jakarta.persistence.Column(length=1000) private String deliveryExceptionReason;
    private ZonedDateTime deliveryCodeExpiresAt;
    private ZonedDateTime deliveryCodeLockedAt;
    private ZonedDateTime deliveryCodeReissuedAt;
    private Long deliveryCodeReissuedBy;
    @jakarta.persistence.Column(length=1000) private String deliveryCodeReissueReason;
    @JsonIgnore private byte[] deliveryRecoveryOtp;
    private ZonedDateTime deliveryRecoveryOtpExpiresAt;
    private ZonedDateTime deliveryRecoveryRequestedAt;
    private ZonedDateTime deliveryRecoveryWindowStartedAt;
    private int deliveryRecoveryRequestCount;
    private int deliveryRecoveryOtpAttempts;
    private ZonedDateTime deliveryRecoveryCompletedAt;
    private Long deliveryRecoveryRequestedBy;
    @jakarta.persistence.Column(length=1000) private String deliveryRecoverySupportReason;
    private String deliveryProofContentType;
    private Long deliveryProofSize;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"orderNumber\":\""+orderNumber+"\",\"status\":\""+status+"\",\"paymentStatus\":\""+paymentStatus+"\",\"refundStatus\":\""+refundStatus+"\",\"settlementStatus\":\""+settlementStatus+"\"}";}
}
