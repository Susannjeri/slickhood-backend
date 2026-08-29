package org.pms.silverocean.controller.wrappers;

public record SubscriptionOverviewDTO(
        SubscriptionCurrentDTO subscription,
        int propertiesUsed,
        int unitsUsed,
        boolean cancellationScheduled,
        String scheduledPlanCode
) {
}
