package org.pms.silverocean.service.property.wrappers;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public interface UnitChargeProjection {
    long getId();
    ZonedDateTime getCreatedOn();
    long getChargeId();
    String getChargeName();
    double getAmount();
    String getPeriodId();
    LocalDate getNextPaymentDate();
}
