package org.pms.silverocean.service.subscription;

import jakarta.validation.constraints.NotNull;

public record SubscriptionAdminUpdateRequest(@NotNull Boolean autoRenew) {
}
