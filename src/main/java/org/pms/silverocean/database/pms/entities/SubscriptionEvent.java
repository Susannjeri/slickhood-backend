package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.subscription.enums.SubscriptionEventType;

@Table(name = "pms_subscription_event", indexes = {
        @Index(name = "idx_subscription_event_type", columnList = "eventType, active")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEvent extends BaseCreatorEntity {
    @Enumerated(EnumType.STRING)
    private SubscriptionEventType eventType;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userSubscription_id")
    private UserSubscription userSubscription;
}
