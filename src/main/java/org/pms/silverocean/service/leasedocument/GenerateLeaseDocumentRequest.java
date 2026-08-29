package org.pms.silverocean.service.leasedocument;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GenerateLeaseDocumentRequest(Long leaseId, Long propertyId, Long recipientUserId,
        @NotNull LeaseDocumentType documentType, LocalDate effectiveDate, LocalDate responseDueDate,
        BigDecimal amount, String currency, @Size(max = 1000) String reason) {}
