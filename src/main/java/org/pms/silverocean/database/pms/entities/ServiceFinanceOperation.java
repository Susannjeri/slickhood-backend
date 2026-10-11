package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

import java.math.BigDecimal;

/**
 * Immutable evidence of a confirmed external Services refund or settlement.
 *
 * <p>The booking remains the current workflow projection. This table is the
 * append-only record that prevents an external reference from being attached
 * to a second booking.</p>
 */
@Entity
@Table(name = "pms_sp_finance_operation",
        uniqueConstraints = @UniqueConstraint(name = "uk_sp_finance_provider_channel_type_ref",
                columnNames = {"provider_user_id", "payment_channel", "operation_type", "provider_reference"}),
        indexes = @Index(name = "idx_sp_finance_booking_created", columnList = "booking_id,created_on"))
@Getter
@NoArgsConstructor
public class ServiceFinanceOperation extends BaseIDEntity {
    @Column(name = "booking_id", nullable = false, updatable = false)
    private long bookingId;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private long providerUserId;

    @Column(name = "payment_channel", nullable = false, updatable = false, length = 30)
    private String paymentChannel;

    @Column(name = "operation_type", nullable = false, updatable = false, length = 30)
    private String operationType;

    @Column(name = "operation_status", nullable = false, updatable = false, length = 30)
    private String operationStatus;

    @Column(name = "provider_reference", nullable = false, updatable = false, length = 120)
    private String providerReference;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 12)
    private String currency;

    @Column(name = "initiated_by", updatable = false)
    private Long initiatedBy;

    public ServiceFinanceOperation(long bookingId, long providerUserId, String paymentChannel,
                                   String operationType, String providerReference,
                                   BigDecimal amount, String currency, Long initiatedBy) {
        this.bookingId = bookingId;
        this.providerUserId = providerUserId;
        this.paymentChannel = paymentChannel;
        this.operationType = operationType;
        this.operationStatus = "CONFIRMED";
        this.providerReference = providerReference;
        this.amount = amount;
        this.currency = currency;
        this.initiatedBy = initiatedBy;
    }
}
