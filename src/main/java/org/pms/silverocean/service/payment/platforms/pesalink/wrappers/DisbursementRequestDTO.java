package org.pms.silverocean.service.payment.platforms.pesalink.wrappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DisbursementRequestDTO(String requestId,
                                     LocalDateTime requestDate,
                                     String login,
                                     String hash,
                                     String serviceId,
                                     String description,
                                     String currencyCode,
                                     String purpose,
                                     BigDecimal sourceAmount,
                                     String sourceAccountNumber,
                                     String sourceBankCode,
                                     String destinationAccount,
                                     String sourceCustomerName,
                                     String destinationBankCode,
                                     String sourceMsisdn) {
}
