package org.pms.silverocean.service.invites;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface PendingTenantInviteProjection {
    Long getInviteId();
    String getToken();
    Long getUnitId();
    String getUnitRef();
    String getPropertyName();
    LocalDate getLeaseStartDate();
    LocalDate getLeaseEndDate();
    LocalDateTime getExpiryDate();
}
