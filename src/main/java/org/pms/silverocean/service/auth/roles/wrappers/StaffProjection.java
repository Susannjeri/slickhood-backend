package org.pms.silverocean.service.auth.roles.wrappers;

import java.time.ZonedDateTime;

public interface StaffProjection {
    long getStaffId();
    String getEmail();
    String getName();
    String getType();
    ZonedDateTime getJoinedOn();
}
