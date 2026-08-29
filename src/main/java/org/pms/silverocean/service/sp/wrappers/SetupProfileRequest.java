package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotNull;

public record SetupProfileRequest(
    String businessName,
    @NotNull(message = "Consent is required") Boolean consent,
    Double latitude,
    Double longitude
) {}
