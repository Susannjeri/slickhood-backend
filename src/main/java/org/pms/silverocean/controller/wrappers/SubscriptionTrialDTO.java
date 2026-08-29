package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionTrialDTO(@NotBlank String role, @NotBlank String planCode) {
}
