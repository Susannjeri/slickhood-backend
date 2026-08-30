package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteServiceStaffRoleTest {

    @Test
    void permitsOnlyControlledSlickHoodAndSilverwoodStaffRoles() {
        assertTrue(InviteService.isInternalStaffRole(PMSRole.SUPPORT));
        assertTrue(InviteService.isInternalStaffRole(PMSRole.SALES_MARKETING));
        assertTrue(InviteService.isInternalStaffRole(PMSRole.FINANCE));
        assertTrue(InviteService.isInternalStaffRole(PMSRole.INSURANCE_ADVISER));
        assertTrue(InviteService.isInternalStaffRole(PMSRole.INSURANCE_MANAGER));

        assertFalse(InviteService.isInternalStaffRole(PMSRole.SUPER_ADMIN));
        assertFalse(InviteService.isInternalStaffRole(PMSRole.LANDLORD));
        assertFalse(InviteService.isInternalStaffRole(PMSRole.WORKSPACE_ADMIN));
    }
}
