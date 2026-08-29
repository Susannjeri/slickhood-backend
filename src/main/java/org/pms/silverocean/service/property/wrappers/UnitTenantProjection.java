package org.pms.silverocean.service.property.wrappers;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public interface UnitTenantProjection {
    long getId();

    long getUserId();

    String getName();

    String getEmail();

    String getPhoneNumber();

    ZonedDateTime getCreatedOn();

    long getLeaseId();

    boolean isLeaseAccepted();

    LocalDateTime getTenantSignedDate();

    String getSignedByManagerName();

    LocalDateTime getManagerSignedDate();
}
