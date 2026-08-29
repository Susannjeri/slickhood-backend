package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Size;

public record SubscriptionCancelDTO(@Size(max = 500) String reason) {
}
