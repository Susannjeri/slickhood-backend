package org.pms.silverocean.controller.wrappers;

import java.util.List;

public record SubscriptionOverviewDTO(
        SubscriptionCurrentDTO subscription,
        int propertiesUsed,
        int unitsUsed,
        boolean cancellationScheduled,
        String scheduledPlanCode,
        List<String> effectiveFeatures,
        List<SubscriptionEffectiveAddOnDTO> activeAddOns
) {
}
