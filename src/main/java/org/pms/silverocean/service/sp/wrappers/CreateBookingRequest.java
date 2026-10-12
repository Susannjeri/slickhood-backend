package org.pms.silverocean.service.sp.wrappers;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.pms.silverocean.config.ValidCurrency;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record CreateBookingRequest(
    @NotNull @Positive Long serviceId,
    @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledAt,
    @Size(max=2000) String notes,
    @Positive Long propertyId,
    @Positive Long unitId,
    @Size(max=500) String serviceAddress,
    @Size(max=100) String idempotencyKey,
    @DecimalMin("1") @DecimalMax("999") @Digits(integer = 3, fraction = 0) BigDecimal unitQuantity,
    @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal expectedUnitRate,
    @NotNull @ValidCurrency String expectedCurrency,
    @NotNull PricingUnit expectedPricingUnit
) {
    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId, Long unitId) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, null, null, BigDecimal.ONE,
                null, null, null);
    }
    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId, Long unitId, String idempotencyKey) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, null, idempotencyKey, BigDecimal.ONE,
                null, null, null);
    }
    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId,
                                Long unitId, String serviceAddress, String idempotencyKey) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, serviceAddress, idempotencyKey, BigDecimal.ONE,
                null, null, null);
    }

    public CreateBookingRequest(Long serviceId, LocalDateTime scheduledAt, String notes, Long propertyId,
                                Long unitId, String serviceAddress, String idempotencyKey,
                                BigDecimal unitQuantity) {
        this(serviceId, scheduledAt, notes, propertyId, unitId, serviceAddress, idempotencyKey, unitQuantity,
                null, null, null);
    }
}
