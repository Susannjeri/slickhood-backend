package org.pms.silverocean.controller.wrappers;

import org.pms.silverocean.database.pms.entities.UserSubscription;
import org.pms.silverocean.service.subscription.enums.SubscriptionStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record SubscriptionCurrentDTO(
        UUID uuid,
        String role,
        String planCode,
        String productKey,
        long termVersion,
        SubscriptionStatus status,
        ZonedDateTime startAt,
        ZonedDateTime endAt,
        boolean autoRenew,
        SubscriptionPlanSummaryRestDTO planDetails
) {
    public static SubscriptionCurrentDTO from(UserSubscription sub, SubscriptionPlanSummaryRestDTO planDetails) {
        return new SubscriptionCurrentDTO(
                sub.getUuid(),
                sub.getRole().name(),
                sub.getPlanCode(),
                sub.getProductKey() == null ? sub.getRole().name() : sub.getProductKey().name(),
                sub.getTermVersion(),
                sub.getStatus(),
                sub.getStartAt(),
                sub.getEndAt(),
                sub.isAutoRenew(),
                planDetails
        );
    }
}
