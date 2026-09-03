package org.pms.silverocean.service.payment.wrappers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentInitRequest(
        @NotBlank @Size(max = 100) String invoiceRef,
        @NotNull PaymentChannel paymentChannel,
        @Size(max = 20) String phoneNumber,
        @Positive long accountId) {
}
