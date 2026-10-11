package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RateServiceRequest(
    @NotNull @Positive Long bookingId,
    @Min(1) @Max(5) int stars,
    @Size(max = 1000) String comment
) {}
