package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ApproveProviderProfileRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 1000) String adminNotes) {
}
