package org.pms.silverocean.service.lease.wrappers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@AllArgsConstructor
@Getter
public class UnitChargeProjectionDTO  implements UnitChargeProjection {
    private long id;
    private ZonedDateTime createdOn;
    private long chargeId;
    private String chargeName;
    private double amount;
    private String periodId;
    private LocalDate nextPaymentDate;
}
