package org.pms.silverocean.service.lease.wrappers;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeaseTerminationRequest(@NotNull @FutureOrPresent LocalDate effectiveDate,
                                      @NotBlank @Size(max = 1000) String reason) {}
