package org.pms.silverocean.service.subscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.subscription.enums.BillingCycle;
import org.pms.silverocean.service.subscription.enums.PlanCategory;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionPlanRequestDTO(
        @NotBlank String code,
        @NotBlank String displayName,
        @NotNull PlanCategory planCategory,
        @NotNull PMSRole roleFamily,
        @NotNull BillingCycle billingCycle,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotBlank String currency,
        @Valid List<PlanFeatureDTO> features,
        @Valid List<PlanQuotaDTO> quotas
) {
}
