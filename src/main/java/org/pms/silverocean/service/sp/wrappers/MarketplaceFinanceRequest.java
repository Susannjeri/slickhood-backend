package org.pms.silverocean.service.sp.wrappers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MarketplaceFinanceRequest(
        @NotNull FinanceType type,
        @NotNull FinanceStatus status,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 120) String providerReference
) {
    public enum FinanceType { REFUND, SETTLEMENT }
    public enum FinanceStatus { REQUESTED, PROCESSING, CONFIRMED, FAILED }
}
