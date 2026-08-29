package org.pms.silverocean.service.subscription;

import jakarta.validation.constraints.NotBlank;

public record PlanFeatureDTO(
        @NotBlank String featureKey,
        boolean enabled
) {
}
