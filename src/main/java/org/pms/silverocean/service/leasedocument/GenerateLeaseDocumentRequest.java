package org.pms.silverocean.service.leasedocument;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GenerateLeaseDocumentRequest(Long leaseId, Long saleId, Long propertyId, Long recipientUserId,
        @NotNull LeaseDocumentType documentType, LocalDate effectiveDate, LocalDate responseDueDate,
        @jakarta.validation.constraints.DecimalMin("0.01") @jakarta.validation.constraints.Digits(integer=12, fraction=2) BigDecimal amount,
        @jakarta.validation.constraints.Pattern(regexp="[A-Za-z]{3}") String currency,
        @Size(max = 1000) String reason, Long ownershipId) {
    public GenerateLeaseDocumentRequest(Long leaseId, Long saleId, Long propertyId, Long recipientUserId,
            LeaseDocumentType documentType, LocalDate effectiveDate, LocalDate responseDueDate,
            BigDecimal amount, String currency, String reason) {
        this(leaseId, saleId, propertyId, recipientUserId, documentType, effectiveDate, responseDueDate, amount, currency, reason, null);
    }
}
