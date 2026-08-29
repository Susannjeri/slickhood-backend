package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FileComplaintRequest(
    @NotNull @Positive Long bookingId,
    @NotBlank String description
) {}
