package org.pms.silverocean.service.lease.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.pms.silverocean.database.pms.entities.Lease;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaseDTO(Long id,
                       String name,
                       PMSLeaseMode leaseMode,
                       boolean selfRenew,
                       LocalDate expiryDate,
                       boolean signed,
                       String tenantName,
                       LocalDateTime lastModifiedDate,
                       ZonedDateTime lastCreatedOn,
                       LocalDateTime tenantSignDate,
                       LocalDateTime ownerSignDate,
                       String ownerSignName,
                       boolean governedDocumentRequired,
                       String lifecycleStatus,
                       LocalDate terminationEffectiveDate, LocalDate moveInDate, double price, String currency,
                       Long agreementDocumentId, String agreementStatus) {
    public LeaseDTO(Lease lease, String tenantName, String ownerSignName) {
        this(lease.getId(), lease.getName(), PMSLeaseMode.valueOf(lease.getLeaseMode()), lease.isSelfRenew(), lease.getMoveOutDate(),
                lease.isSigned(), tenantName, lease.getLastModifiedDate(), lease.getCreatedOn(), lease.getTenantSignedDate(),
                lease.getManagerSignedDate(), ownerSignName, lease.isGovernedDocumentRequired(),
                lease.getLifecycleStatus(), lease.getTerminationEffectiveDate(), lease.getMoveInDate(), lease.getPrice(), lease.getCurrency(),
                null, null);
    }

    public LeaseDTO withAgreement(Long documentId, String status) {
        return new LeaseDTO(id, name, leaseMode, selfRenew, expiryDate, signed, tenantName, lastModifiedDate,
                lastCreatedOn, tenantSignDate, ownerSignDate, ownerSignName, governedDocumentRequired,
                lifecycleStatus, terminationEffectiveDate, moveInDate, price, currency, documentId, status);
    }
}
