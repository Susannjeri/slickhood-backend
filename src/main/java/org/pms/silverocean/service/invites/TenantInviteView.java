package org.pms.silverocean.service.invites;

import org.pms.silverocean.service.property.wrappers.UnitDTO;

import java.time.LocalDate;

/** Public, non-secret terms frozen by the landlord when a tenant is invited. */
public record TenantInviteView(UnitDTO unit, LocalDate leaseStartDate, LocalDate leaseEndDate) {
}
