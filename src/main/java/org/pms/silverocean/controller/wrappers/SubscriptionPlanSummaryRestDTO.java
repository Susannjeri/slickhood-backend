package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.service.subscription.PlanFeatureDTO;
import org.pms.silverocean.service.subscription.PlanQuotaDTO;
import org.pms.silverocean.service.subscription.SubscriptionPlanResponseDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * JSON-stable plan snapshot without loading persona enums into the REST layer classpath graph.
 */
public record SubscriptionPlanSummaryRestDTO(
        UUID uuid,
        String code,
        String displayName,
        String planCategory,
        String roleFamily,
        String billingCycle,
        BigDecimal price,
        String currency,
        boolean active,
        List<PlanFeatureDTO> features,
        List<PlanQuotaDTO> quotas
) {
    public static SubscriptionPlanSummaryRestDTO from(SubscriptionPlanResponseDTO plan) {
        return new SubscriptionPlanSummaryRestDTO(
                plan.uuid(),
                plan.code(),
                plan.displayName(),
                plan.planCategory().name(),
                plan.roleFamily().name(),
                plan.billingCycle().name(),
                plan.price(),
                plan.currency(),
                plan.active(),
                plan.features(),
                plan.quotas()
        );
    }
}
