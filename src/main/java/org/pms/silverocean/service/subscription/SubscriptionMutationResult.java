package org.pms.silverocean.service.subscription;

import org.pms.silverocean.controller.wrappers.SubscriptionCurrentDTO;

public record SubscriptionMutationResult(
        boolean requiresPayment,
        SubscriptionCurrentDTO assignedSubscription,
        SubscriptionPendingCheckoutDTO pendingPayment
) {
    public static SubscriptionMutationResult freeAssigned(SubscriptionCurrentDTO current) {
        return new SubscriptionMutationResult(false, current, null);
    }

    public static SubscriptionMutationResult pendingInvoice(SubscriptionPendingCheckoutDTO pending) {
        return new SubscriptionMutationResult(true, null, pending);
    }
}
