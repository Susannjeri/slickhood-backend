package org.pms.silverocean.service.subscription;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SubscriptionPlanResponseDTO(
        UUID uuid,
        String code,
        String displayName,
        PlanCategory planCategory,
        PMSRole roleFamily,
        BillingCycle billingCycle,
        BigDecimal price,
        String currency,
        boolean active,
        List<PlanFeatureDTO> features,
        List<PlanQuotaDTO> quotas
) {
}
