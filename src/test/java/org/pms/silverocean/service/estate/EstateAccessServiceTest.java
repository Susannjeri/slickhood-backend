package org.pms.silverocean.service.estate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstateAccessServiceTest {
    @Mock PropertyRepo properties;
    @Mock UserDao users;
    @Mock WorkspaceSelectionService workspaces;
    EstateAccessService service;
    Property estate;
    @BeforeEach void setup() {
        service = new EstateAccessService(properties, users, workspaces);
        estate = new Property(); estate.setId(11L); estate.setActive(true);
        estate.setManagementMode(PMSPropertyManagementMode.SERVICE_CHARGE);
        lenient().when(users.getUserId()).thenReturn(9L);
    }
    @Test void estateOwnerCanManageWithoutStaffAssignment() {
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L,9L)).thenReturn(Optional.of(estate));
        assertSame(estate, service.require(11L,Permission.MANAGE_ESTATE));
        verifyNoInteractions(workspaces);
    }
    @Test void rentalAndSalePropertiesCannotBeUsedAsEstates() {
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L,9L)).thenReturn(Optional.of(estate));
        for (var mode : new PMSPropertyManagementMode[]{PMSPropertyManagementMode.RENTAL,PMSPropertyManagementMode.SALE}) {
            estate.setManagementMode(mode);
            assertThrows(PMSCustomException.class, () -> service.require(11L,Permission.MANAGE_ESTATE));
        }
    }
    @Test void dormantEstateRoleDoesNotPermitLandlordEstateManagement() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        assertThrows(PMSCustomException.class, () -> service.require(11L,Permission.MANAGE_ESTATE));
        verifyNoInteractions(properties,workspaces);
    }
    @Test void accountantUsesSelectedMembershipForBillingNotAnyStaffAssignment() {
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_ACCOUNTANT);
        when(users.hasPermission(Permission.CREATE_SERVICE_CHARGE)).thenReturn(true);
        WorkspaceMembership member = new WorkspaceMembership(); member.setId(7L);
        when(workspaces.selectedMembership(9L)).thenReturn(Optional.of(member));
        when(properties.findByIdAndManagerRoleAndInviteId(11L,9L,"PROPERTY_ACCOUNTANT",-7L)).thenReturn(Optional.of(estate));
        assertSame(estate,service.require(11L,Permission.CREATE_SERVICE_CHARGE));
        assertThrows(PMSCustomException.class, () -> service.require(12L,Permission.CREATE_SERVICE_CHARGE));
        verify(properties,never()).findByIdAndStaffOrOwner(anyLong(),anyLong());
    }
    @Test void employeeWithoutActiveMembershipFailsClosed() {
        when(users.getActiveRole()).thenReturn(PMSRole.ESTATE_OPERATIONS_MANAGER);
        when(users.hasPermission(Permission.MANAGE_ESTATE)).thenReturn(true);
        assertThrows(PMSCustomException.class, () -> service.require(11L,Permission.MANAGE_ESTATE));
        verifyNoInteractions(properties);
    }
    @Test void formerHomeownerCannotReadEstateOperations() {
        when(users.getActiveRole()).thenReturn(PMSRole.HOMEOWNER);
        assertThrows(PMSCustomException.class, () -> service.require(11L,Permission.VIEW_ESTATE));
        verify(properties).findByIdAndHomeowner(11L,9L);
        verify(users,never()).hasRole(PMSRole.SUPER_ADMIN);
    }
}
