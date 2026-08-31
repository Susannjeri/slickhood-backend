package org.pms.silverocean.service.lease.wrappers;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InitLeaseDTO(@NotBlank String token, @NotNull @FutureOrPresent LocalDate moveInDate,
                           @NotNull @FutureOrPresent LocalDate moveOutDate) {
}
