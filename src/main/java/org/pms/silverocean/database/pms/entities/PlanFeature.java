package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
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

@Table(name = "pms_plan_feature", indexes = {
        @Index(name = "idx_plan_feature_lookup", columnList = "subscriptionPlan_id, featureKey")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature extends BaseCreatorEntity {
    private String featureKey;
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriptionPlan_id")
    private SubscriptionPlan subscriptionPlan;
}
