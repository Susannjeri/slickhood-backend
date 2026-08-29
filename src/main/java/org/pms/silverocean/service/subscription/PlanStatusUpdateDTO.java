package org.pms.silverocean.service.subscription;

import jakarta.validation.constraints.NotNull;

public record PlanStatusUpdateDTO(
        @NotNull Boolean active
) {
}
