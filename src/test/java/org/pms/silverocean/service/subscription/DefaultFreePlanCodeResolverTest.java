package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultFreePlanCodeResolverTest {
    @Test
    void affiliateIsAnApprovedProgrammeNotASubscriptionRole() {
        var resolver = new DefaultFreePlanCodeResolver(
                "LANDLORD_BRONZE", "SERVICES_FREE", "WEALTH_BRONZE", "ESTATE_BRONZE", "SALE_BRONZE");

        assertNull(resolver.resolvePlanCode(PMSRole.AFFILIATE));
        assertFalse(resolver.isProvisioningRole(PMSRole.AFFILIATE));
    }
}
