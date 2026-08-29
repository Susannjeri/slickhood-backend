package org.pms.silverocean.service.property.charges;

import java.time.ZonedDateTime;

public record FormattedUnitChargesDTO(long id, ZonedDateTime createdOn, long chargeId, String chargeName, double amount, String periodId, String periodName) {
}
