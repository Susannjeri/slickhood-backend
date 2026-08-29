package org.pms.silverocean.service.sales;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateSaleRequest(@NotNull SaleStatus status, BigDecimal offerAmount, String notes) {}
