package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionSalesRequestDTO(
        @NotBlank String planCode,
        @Size(max = 1000) String message
) {
}
