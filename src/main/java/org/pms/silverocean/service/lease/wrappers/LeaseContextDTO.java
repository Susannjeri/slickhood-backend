package org.pms.silverocean.service.lease.wrappers;

public record LeaseContextDTO(long unitId, long tenantUserId, String propertyName, long landlordUserId, String senderRoleName) {
}
