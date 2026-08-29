package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotNull;

public record SubscriptionAutoRenewDTO(@NotNull Boolean enabled) {
}
