package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SubscriptionSubscribeRestDTO(
        @NotBlank String role,
        @NotBlank String planCode,
        @Positive Long paymentAccountId
) {
}
