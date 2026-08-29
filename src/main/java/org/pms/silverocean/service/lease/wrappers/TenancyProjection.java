package org.pms.silverocean.service.lease.wrappers;

import java.time.LocalDateTime;

public interface TenancyProjection {

    long getTenancyId();
    long LeaseId();

    long getUnitId();

    String getUnitRef();

    String getPropertyName();

    boolean getLeaseAccepted();

    LocalDateTime getRequestedOn();

}