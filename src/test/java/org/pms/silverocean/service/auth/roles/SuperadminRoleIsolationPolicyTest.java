package org.pms.silverocean.service.auth.roles;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuperadminRoleIsolationPolicyTest {
    private final UserRoleRepo userRoles = mock(UserRoleRepo.class);
    private final SuperadminRoleIsolationPolicy policy = new SuperadminRoleIsolationPolicy(userRoles);

    @Test
    void superadminCannotAssumeLandlordRole() {
        when(userRoles.findByUserId(7L)).thenReturn(Set.of(role(PMSRole.SUPER_ADMIN, 1L)));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> policy.assertCanAssume(7L, PMSRole.LANDLORD));

        assertEquals(ResponseCode.SUPERADMIN_ROLE_ISOLATED, error.getResponseCode());
    }

    @Test
    void ordinaryCustomerCanAddAnotherCustomerRole() {
        when(userRoles.findByUserId(8L)).thenReturn(Set.of(role(PMSRole.LANDLORD, 2L)));

        assertDoesNotThrow(() -> policy.assertCanAssume(8L, PMSRole.SALES_AGENT));
    }

    @Test
    void legacyMixedAccountExposesOnlySuperadminRole() {
        Role superadmin = role(PMSRole.SUPER_ADMIN, 1L);
        Role landlord = role(PMSRole.LANDLORD, 2L);

        Set<Role> effective = policy.effectiveRoles(Set.of(superadmin, landlord));

        assertEquals(Set.of(superadmin), effective);
    }

    private Role role(PMSRole value, long id) {
        Role role = new Role(value.getName(), value.getDescription(), value.isSelfAssignable());
        role.setId(id);
        role.setActive(true);
        return role;
    }
}
