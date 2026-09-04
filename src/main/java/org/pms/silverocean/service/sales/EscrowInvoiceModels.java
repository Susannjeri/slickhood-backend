package org.pms.silverocean.service.sales;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EscrowInvoiceModels {
    private EscrowInvoiceModels() {}

    public record Create(@NotNull @DecimalMin("0.01") BigDecimal amount) {}

    public record View(long invoiceId, String invoiceRef, BigDecimal amount, String currency,
                       boolean paid, BigDecimal pendingAmount, LocalDate dueDate) {}
}
