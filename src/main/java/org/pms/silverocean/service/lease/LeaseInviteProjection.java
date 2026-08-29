package org.pms.silverocean.service.lease;

import java.time.LocalDateTime;

public interface LeaseInviteProjection {
    long getInviteId();
    long getEntityId();
    String getUnitRef();
    String getPropertyName();
    LocalDateTime getLastAccessed();
    LocalDateTime getExpiryDate();
    int getVisits();
    String getToken();
}
