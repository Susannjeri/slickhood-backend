package org.pms.silverocean.service.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateSaleRequest(@Positive long propertyId, @NotNull @Positive Long unitId, @Positive Long buyerUserId,
                                @Email @Size(max=254) String buyerEmail,
                                @NotNull @Positive BigDecimal askingPrice, @NotBlank @jakarta.validation.constraints.Size(max=12) String currency,
                                @jakarta.validation.constraints.Size(max=1000) String notes) {}
