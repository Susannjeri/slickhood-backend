package org.pms.silverocean.controller.wrappers;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.service.property.charges.PMSPeriod;

public record ChargeDTO(@NotNull(message = "chargeId is missing") @Min(1) Long chargeId, @NotNull(message = "period is missing") PMSPeriod period, @NotNull(message = "amount is missing")  @Min(1) Double amount) {
}
