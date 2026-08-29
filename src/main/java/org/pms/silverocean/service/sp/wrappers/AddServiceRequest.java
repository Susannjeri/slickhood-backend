package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.pms.silverocean.config.ValidCurrency;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.math.BigDecimal;

public record AddServiceRequest(
    @NotNull @Positive Long categoryId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull @ValidCurrency String currency,
    @NotNull PricingUnit pricingUnit
) {}
