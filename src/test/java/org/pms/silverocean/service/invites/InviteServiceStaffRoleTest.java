package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.users.StaffInviteRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void customerOwnerCannotUseDormantPlatformOwnerAuthorityToInviteInternalStaff() {
        UserDao users = mock(UserDao.class);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        InviteService service = new InviteService(null, null, users, null, null, null, null, null, null);

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> service.createInternalStaffInvite(new StaffInviteRequest("staff@slickhood.com", PMSRole.SUPPORT)));

        assertEquals(ResponseCode.INVALID_USER_DETAILS, exception.getResponseCode());
    }
}
