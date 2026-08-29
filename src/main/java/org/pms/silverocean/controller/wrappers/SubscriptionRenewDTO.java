package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Positive;

public record SubscriptionRenewDTO(@Positive Long paymentAccountId) {
}
