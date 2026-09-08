package org.pms.silverocean.service.invites;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PendingTenantInviteDTO(long inviteId, String token, long unitId, String unitRef,
                                     String propertyName, LocalDate leaseStartDate,
                                     LocalDate leaseEndDate, LocalDateTime expiresAt) {
    public PendingTenantInviteDTO(PendingTenantInviteProjection invite) {
        this(invite.getInviteId(), invite.getToken(), invite.getUnitId(), invite.getUnitRef(),
                invite.getPropertyName(), invite.getLeaseStartDate(), invite.getLeaseEndDate(),
                invite.getExpiryDate());
    }
}
