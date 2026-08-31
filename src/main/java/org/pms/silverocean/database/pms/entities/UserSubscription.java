package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;

import java.time.ZonedDateTime;

@Table(name = "pms_user_subscription", indexes = {
        @Index(name = "idx_user_subscription_active", columnList = "createdBy, status, active")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription extends BaseCreatorEntity {
    @Enumerated(EnumType.STRING)
    private PMSRole role;

    private String planCode;

    @Enumerated(EnumType.STRING)
    private SubscriptionProduct productKey;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private ZonedDateTime startAt;
    private ZonedDateTime endAt;
    private boolean autoRenew;
    private String sourcePaymentRef;
    private long termVersion;
}
