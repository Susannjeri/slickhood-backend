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

@Table(name = "pms_plan_quota", indexes = {
        @Index(name = "idx_plan_quota_lookup", columnList = "subscriptionPlan_id, metricKey")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanQuota extends BaseCreatorEntity {
    private String metricKey;
    private Long limitValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriptionPlan_id")
    private SubscriptionPlan subscriptionPlan;
}
