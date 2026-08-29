package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RateServiceRequest(
    @NotNull @Positive Long bookingId,
    @Min(1) @Max(5) int stars,
    String comment
) {}
