package org.pms.silverocean.service.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

public record SubscribeOrUpgradeRequestDTO(
        @NotNull PMSRole role,
        @NotBlank String planCode,
        @Positive Long paymentAccountId
) {
}
