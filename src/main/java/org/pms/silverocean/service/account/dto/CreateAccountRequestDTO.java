package org.pms.silverocean.service.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

public record CreateAccountRequestDTO(
        @NotNull PaymentChannel channel,
        @NotBlank String name,
        @NotNull AccountCategory category
) {}
