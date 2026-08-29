package org.pms.silverocean.service.reports;

import java.time.LocalDate;

public interface LeaseExpiryProjection {
    Long getLeaseId();
    Long getPropertyId();
    Long getUnitId();
    String getUnitRef();
    Long getTenantUserId();
    LocalDate getMoveInDate();
    LocalDate getMoveOutDate();
    Boolean getSigned();
    Boolean getSelfRenew();
    Integer getNoticePeriodInMonths();
    String getCurrency();
    Double getPrice();
}
