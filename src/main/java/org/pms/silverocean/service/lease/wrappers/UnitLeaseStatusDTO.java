package org.pms.silverocean.service.lease.wrappers;

import java.time.LocalDateTime;

public record UnitLeaseStatusDTO(Long unitId, Long leaseId, LocalDateTime tenantSignedDate,
                                 LocalDateTime ownerSignedDate) {
    public LeaseIdTenantSignDateDTO leaseStatus() {
        return new LeaseIdTenantSignDateDTO(leaseId, tenantSignedDate, ownerSignedDate);
    }
}
