package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import java.time.LocalDateTime;

public record ValidationRequestDTO(
        String requestId,
        String login,
        LocalDateTime requestDate,
        String hash,
        String serviceId,
        String description,
        String currencyCode,
        String sourceBankCode,
        String destinationBankCode,
        String destinationAccount
) {}
