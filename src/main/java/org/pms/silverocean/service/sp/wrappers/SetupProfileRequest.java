package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

public record SetupProfileRequest(
    @NotBlank @Size(max = 255) String businessName,
    @NotNull(message = "Consent is required") Boolean consent,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @Size(max = 1000) String description,
    @Size(max = 255) String serviceArea
) {
    public SetupProfileRequest(String businessName, Boolean consent, Double latitude, Double longitude) {
        this(businessName, consent, latitude, longitude, null, null);
    }
}
