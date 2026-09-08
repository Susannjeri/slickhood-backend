package org.pms.silverocean.service.lease.wrappers;

import java.time.LocalDate;

public record LeaseInitializationResult(long leaseId, long agreementDocumentId,
                                        LocalDate leaseStartDate, LocalDate leaseEndDate,
                                        String agreementStatus) {
}
