package org.pms.silverocean.service.estate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record OwnershipTerminationRequest(
        @NotNull @PastOrPresent LocalDate endDate,
        @NotBlank @Size(max = 500) String reason) {
}
