package org.pms.silverocean.service.lease.wrappers;

import java.time.LocalDateTime;

public record LeaseIdTenantSignDateDTO(Long id, LocalDateTime tenantSignedDate, LocalDateTime ownerSignedDate) {
}
