package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RejectProviderProfileRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 1000) String reason) {
}
