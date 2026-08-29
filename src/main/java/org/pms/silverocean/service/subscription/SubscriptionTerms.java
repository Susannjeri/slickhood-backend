package org.pms.silverocean.service.subscription;

import org.pms.silverocean.service.subscription.enums.BillingCycle;

import java.time.ZonedDateTime;

public final class SubscriptionTerms {
    private SubscriptionTerms() {
    }

    public static ZonedDateTime endAt(BillingCycle billingCycle, ZonedDateTime startAt) {
        if (billingCycle == null || billingCycle == BillingCycle.LIFETIME) {
            return null;
        }
        return switch (billingCycle) {
            case MONTHLY -> startAt.plusMonths(1);
            case QUARTERLY -> startAt.plusMonths(3);
            case YEARLY -> startAt.plusYears(1);
            case LIFETIME -> null;
        };
    }
}
