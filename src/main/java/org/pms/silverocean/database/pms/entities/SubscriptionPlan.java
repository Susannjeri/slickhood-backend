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
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;
import org.pms.silverocean.service.subscription.enums.SubscriptionProduct;
import org.pms.silverocean.service.subscription.enums.SubscriptionPurchaseMode;

import java.math.BigDecimal;

@Table(name = "pms_subscription_plan", indexes = {
        @Index(name = "idx_sub_plan_code", columnList = "code", unique = true),
        @Index(name = "idx_sub_plan_category", columnList = "planCategory, active")
})
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan extends BaseCreatorEntity {
    private String code;
    private String displayName;

    @Enumerated(EnumType.STRING)
    private PlanCategory planCategory;

    @Enumerated(EnumType.STRING)
    private PMSRole roleFamily;

    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;

    private BigDecimal price;
    private String currency;

    @Enumerated(EnumType.STRING)
    private SubscriptionProduct productKey;

    @Enumerated(EnumType.STRING)
    private SubscriptionPurchaseMode purchaseMode;

    private Integer tierRank;
}
