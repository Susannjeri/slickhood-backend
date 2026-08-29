package org.pms.silverocean.service.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlanQuotaDTO(
        @NotBlank String metricKey,
        @NotNull @PositiveOrZero Long limitValue
) {
}
