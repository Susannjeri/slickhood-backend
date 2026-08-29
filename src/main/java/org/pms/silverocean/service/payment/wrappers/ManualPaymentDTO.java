package org.pms.silverocean.service.payment.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ManualPaymentDTO(@NotBlank(message = "Invoice reference is required")
                               @Size(max = 50, message = "Invoice reference must not exceed 50 characters")
                               String invoiceRef,

                               @Positive(message = "Amount must be greater than zero")
                               double amount,

                               @NotBlank(message = "Payment channel is required")
                               @Size(max = 50, message = "Payment Channel must not exceed 50 characters")
                               String channel,

                               @NotBlank(message = "Transaction ID is required")
                               @Size(min = 5, max = 100, message = "Transaction ID length is invalid")
                               String transId,

                               @NotNull(message = "Transaction date is required")
                               @PastOrPresent(message = "Transaction date cannot be in the future")
                               LocalDate transactionDate) {
}
