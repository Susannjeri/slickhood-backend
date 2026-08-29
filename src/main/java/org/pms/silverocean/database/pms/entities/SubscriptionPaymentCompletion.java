package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

/**
 * One row per subscription checkout invoice that has been fully applied to {@link UserSubscription}.
 * Unique {@code invoiceId} ensures idempotent activation on duplicate payment callbacks.
 */
@Table(
        name = "pms_subscription_payment_completion",
        uniqueConstraints = @UniqueConstraint(name = "uk_sub_pay_completion_invoice", columnNames = "invoiceId"),
        indexes = {
                @Index(name = "idx_sub_pay_completion_subscriber", columnList = "subscriberUserId")
        }
)
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPaymentCompletion extends BaseActiveEntity {

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private Long subscriberUserId;

    @Column(nullable = false, length = 64)
    private String planCode;

    @Column(length = 256)
    private String providerReference;
}
