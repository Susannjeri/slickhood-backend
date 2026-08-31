package org.pms.silverocean.service.sales;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateSaleRequest(@NotNull SaleStatus status, @Positive BigDecimal offerAmount, @Size(max=1000) String notes) {}
