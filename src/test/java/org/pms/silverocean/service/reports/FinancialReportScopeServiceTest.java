package org.pms.silverocean.service.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.teamaccess.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialReportScopeServiceTest {
    @Mock UserDao users;
    @Mock WorkspaceSelectionService selection;
    @Mock CustomerWorkspaceRepo workspaces;
    @Mock PropertyRepo properties;
    FinancialReportScopeService service;
    WorkspaceMembership member;
    CustomerWorkspace workspace;

    @BeforeEach void setup() {
        service = new FinancialReportScopeService(users, selection, workspaces, properties, new ObjectMapper());
        when(users.getUserId()).thenReturn(41L);
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_ACCOUNTANT);
        member = new WorkspaceMembership();
        member.setId(9L); member.setUserId(41L); member.setWorkspaceId(3L); member.setActive(true);
        member.setStatus(TeamMembershipStatus.ACTIVE); member.setMembershipRole(TeamMembershipRole.PROPERTY_ACCOUNTANT);
        member.setScopeType(TeamScopeType.ENTIRE_WORKSPACE);
        workspace = new CustomerWorkspace(); workspace.setId(3L); workspace.setActive(true);
        workspace.setOwnerUserId(99L); workspace.setBusinessArea(TeamBusinessArea.ESTATE_MANAGEMENT);
    }
    private void selected() {
        when(selection.selectedMembership(41L)).thenReturn(Optional.of(member));
        when(workspaces.findById(3L)).thenReturn(Optional.of(workspace));
    }
    @Test void bindsQueriesToTheSelectedMembershipOwnerRoleAndAssignment() {
        selected(); when(properties.findFinancialReportPropertyIds(99L,41L,"PROPERTY_ACCOUNTANT",-9L)).thenReturn(List.of(7L,8L));
        var scope=service.resolve();
        assertTrue(scope.restricted()); assertEquals(List.of(7L,8L),scope.propertyIds());
        assertEquals(-9L,scope.assignmentId()); assertEquals("PROPERTY_ACCOUNTANT",scope.roleName());
    }
    @Test void intersectsSelectedResourcesWithLiveAssignmentsInsteadOfTrustingEitherAlone() {
        selected(); member.setScopeType(TeamScopeType.SELECTED_RESOURCES); member.setResourceIdsJson("[7,500]");
        when(properties.findFinancialReportPropertyIds(99L,41L,"PROPERTY_ACCOUNTANT",-9L)).thenReturn(List.of(7L,8L));
        assertEquals(List.of(7L),service.resolve().propertyIds());
    }
    @Test void missingOrAmbiguousWorkspaceCannotFallbackToAllAssignments() {
        when(selection.selectedMembership(41L)).thenReturn(Optional.empty());
        assertThrows(PMSCustomException.class,service::resolve); verifyNoInteractions(properties,workspaces);
    }
    @Test void suspendedMembershipFailsClosed() {
        selected(); member.setStatus(TeamMembershipStatus.SUSPENDED);
        assertThrows(PMSCustomException.class,service::resolve); verifyNoInteractions(properties);
    }
    @Test void inactiveWorkspaceFailsClosed() {
        selected(); workspace.setActive(false);
        assertThrows(PMSCustomException.class,service::resolve); verifyNoInteractions(properties);
    }
    @Test void mismatchedMembershipRoleCannotProvideAccess() {
        selected(); member.setMembershipRole(TeamMembershipRole.GUARD);
        assertThrows(PMSCustomException.class,service::resolve); verifyNoInteractions(properties);
    }
    @Test void malformedResourceScopeFailsClosed() {
        selected(); member.setScopeType(TeamScopeType.SELECTED_RESOURCES); member.setResourceIdsJson("not-json");
        when(properties.findFinancialReportPropertyIds(99L,41L,"PROPERTY_ACCOUNTANT",-9L)).thenReturn(List.of(7L));
        assertThrows(PMSCustomException.class,service::resolve);
    }
    @Test void emptyScopeUsesImpossiblePropertyRatherThanAnUnrestrictedQuery() {
        selected(); when(properties.findFinancialReportPropertyIds(99L,41L,"PROPERTY_ACCOUNTANT",-9L)).thenReturn(List.of());
        assertTrue(service.resolve().restricted()); assertEquals(List.of(-1L),service.resolve().propertyIds());
    }
    @Test void ownerUsesOnlyOwnedPropertiesWithoutCreatingAWorkspace() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(properties.findFinancialReportPropertyIds(41L,41L,"LANDLORD",null)).thenReturn(List.of(7L));
        assertEquals(List.of(7L),service.resolve().propertyIds()); verifyNoInteractions(workspaces,selection);
    }
    @Test void personalTenantDoesNotInheritUnrelatedEmployeeAssignments() {
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        assertFalse(service.resolve().restricted()); assertNull(service.resolve().assignmentId());
        verifyNoInteractions(properties,workspaces,selection);
    }
    @Test void platformScopeStillUsesTheExistingPlatformQueryBranch() {
        when(users.getActiveRole()).thenReturn(PMSRole.FINANCE);
        assertEquals(FinancialReportScopeService.Scope.personal(),service.resolve());
        verifyNoInteractions(properties,workspaces,selection);
    }

    @Test void realWorkspaceHeaderSwitchesScopesAndRejectsForgedOrMissingSelection() {
        var request=new org.springframework.mock.web.MockHttpServletRequest();
        var memberships=mock(WorkspaceMembershipRepo.class);
        var second=new WorkspaceMembership();second.setId(10L);second.setWorkspaceId(4L);second.setUserId(41L);
        second.setActive(true);second.setStatus(TeamMembershipStatus.ACTIVE);
        second.setMembershipRole(TeamMembershipRole.PROPERTY_ACCOUNTANT);second.setScopeType(TeamScopeType.ENTIRE_WORKSPACE);
        var secondWorkspace=new CustomerWorkspace();secondWorkspace.setId(4L);secondWorkspace.setActive(true);
        secondWorkspace.setOwnerUserId(100L);secondWorkspace.setBusinessArea(TeamBusinessArea.LANDLORD);
        when(memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                41L,TeamMembershipRole.PROPERTY_ACCOUNTANT,TeamMembershipStatus.ACTIVE)).thenReturn(List.of(member,second));
        when(workspaces.findById(3L)).thenReturn(Optional.of(workspace));
        when(workspaces.findById(4L)).thenReturn(Optional.of(secondWorkspace));
        when(properties.findFinancialReportPropertyIds(99L,41L,"PROPERTY_ACCOUNTANT",-9L)).thenReturn(List.of(7L));
        when(properties.findFinancialReportPropertyIds(100L,41L,"PROPERTY_ACCOUNTANT",-10L)).thenReturn(List.of(8L));
        var realSelection=new WorkspaceSelectionService(request,users,memberships,workspaces);
        var scoped=new FinancialReportScopeService(users,realSelection,workspaces,properties,new ObjectMapper());
        assertThrows(PMSCustomException.class,scoped::resolve);
        request.addHeader(WorkspaceSelectionService.HEADER,"3");assertEquals(List.of(7L),scoped.resolve().propertyIds());
        request.removeHeader(WorkspaceSelectionService.HEADER);request.addHeader(WorkspaceSelectionService.HEADER,"4");
        assertEquals(List.of(8L),scoped.resolve().propertyIds());
        request.removeHeader(WorkspaceSelectionService.HEADER);request.addHeader(WorkspaceSelectionService.HEADER,"999");
        assertThrows(PMSCustomException.class,scoped::resolve);
    }
}
