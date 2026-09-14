package org.pms.silverocean.service.payment.latefee;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class LateFeePolicyModels {
    private LateFeePolicyModels() {}

    public record Update(
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal percentageRate,
            @Min(0) @Max(365) int graceDays,
            @DecimalMin("0.01") BigDecimal maximumFee,
            boolean enabled) {}

    public record View(String billingType, BigDecimal percentageRate, int graceDays, BigDecimal maximumFee, boolean enabled,
                       boolean configured, LocalDate effectiveFrom) {}
}
