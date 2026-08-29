package org.pms.silverocean.service.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateSaleRequest(@Positive long propertyId, Long unitId, @Positive long buyerUserId,
                                @NotNull @Positive BigDecimal askingPrice, @NotBlank String currency,
                                String notes) {}
