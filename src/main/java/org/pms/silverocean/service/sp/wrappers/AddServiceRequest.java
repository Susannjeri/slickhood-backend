package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.pms.silverocean.config.ValidCurrency;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.math.BigDecimal;

public record AddServiceRequest(
    @NotNull @Positive Long categoryId,
    @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal amount,
    @NotNull @ValidCurrency String currency,
    @NotNull PricingUnit pricingUnit,
    @Size(min = 3, max = 120) String title,
    @Size(min = 20, max = 2000) String description
) {
    /** Keeps older clients source-compatible while they roll onto rich listings. */
    public AddServiceRequest(Long categoryId, BigDecimal amount, String currency, PricingUnit pricingUnit) {
        this(categoryId, amount, currency, pricingUnit, null, null);
    }
}
