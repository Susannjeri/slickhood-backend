package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FileComplaintRequest(
    @NotNull @Positive Long bookingId,
    @NotBlank @Size(max = 2000) String description
) {}
