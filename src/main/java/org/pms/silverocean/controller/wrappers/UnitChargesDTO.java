package org.pms.silverocean.controller.wrappers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UnitChargesDTO(@NotNull(message = "unitId is missing") @Min(1) Long unitId, @NotEmpty @Valid Set<ChargeDTO> charges) {
}
