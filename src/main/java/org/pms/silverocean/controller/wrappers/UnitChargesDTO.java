package org.pms.silverocean.controller.wrappers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UnitChargesDTO(@NotNull(message = "unitId is missing") @Min(1) Long unitId, @NotNull @Valid Set<ChargeDTO> charges) {
}
