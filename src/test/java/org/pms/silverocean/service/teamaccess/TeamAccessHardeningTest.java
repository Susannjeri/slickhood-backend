package org.pms.silverocean.service.teamaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.database.pms.entities.TeamRoleDefinition;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TeamAccessHardeningTest {
    private CustomerWorkspaceRepo workspaces;
    private WorkspaceInvitationRepo invitations;
    private WorkspaceMembershipRepo memberships;
    private PropertyRepo properties;
    private PropertyManagerRepo propertyManagers;
    private UserDao users;
    private WorkspaceSelectionService workspaceSelection;
    private TeamRoleDefinitionRepo definitions;
    private TeamAccessService service;

    @BeforeEach
    void setUp() {
        workspaces = mock(CustomerWorkspaceRepo.class);
        invitations = mock(WorkspaceInvitationRepo.class);
        memberships = mock(WorkspaceMembershipRepo.class);
        properties = mock(PropertyRepo.class);
        propertyManagers = mock(PropertyManagerRepo.class);
        users = mock(UserDao.class);
        workspaceSelection = mock(WorkspaceSelectionService.class);
        definitions = mock(TeamRoleDefinitionRepo.class);
        service = new TeamAccessService(workspaces, invitations, memberships, definitions,
                properties, propertyManagers, mock(UserSubscriptionRepo.class), mock(SubscriptionPlanRepo.class),
                mock(PlanQuotaRepo.class), mock(RoleRepo.class), mock(UserRoleRepo.class), users,
                mock(ConfigService.class), mock(NotificationService.class), mock(I18NService.class),
                mock(AuditLogService.class), new ObjectMapper(), workspaceSelection);
    }

    @Test
    void viewingAnActiveMemberDoesNotRebuildEveryPropertyAssignment() {
        WorkspaceMembership member = new WorkspaceMembership();
        member.setStatus(TeamMembershipStatus.ACTIVE);
        member.setScopeType(TeamScopeType.ENTIRE_WORKSPACE);

        ReflectionTestUtils.invokeMethod(service, "syncKycStatus", member);

        verifyNoInteractions(properties, propertyManagers);
    }

    @Test
    void newPropertyAssignmentQueriesOnlyTheOwnersWorkspaces() {
        CustomerWorkspace workspace = new CustomerWorkspace();
        workspace.setId(8L);
        when(workspaces.findAllByOwnerUserIdAndActiveTrue(42L)).thenReturn(List.of(workspace));
        when(memberships.findByWorkspaceIdAndStatusAndScopeTypeAndActiveTrue(
                8L, TeamMembershipStatus.ACTIVE, TeamScopeType.ENTIRE_WORKSPACE)).thenReturn(List.of());

        service.assignNewProperty(42L, 99L);

        verify(workspaces).findAllByOwnerUserIdAndActiveTrue(42L);
        verify(workspaces, never()).findAll();
    }

    @Test
    void invitationAndSeatCriticalSectionsUseDatabaseLocks() throws Exception {
        assertThat(CustomerWorkspaceRepo.class.getMethod("findLockedByIdAndActiveTrue", long.class)
                .getAnnotation(Lock.class)).isNotNull();
        assertThat(WorkspaceInvitationRepo.class.getMethod("findLockedByTokenHashAndActiveTrue", String.class)
                .getAnnotation(Lock.class)).isNotNull();
    }

    @Test
    void legacyRoleLookupCannotReturnRevokedPropertyAssignments() throws Exception {
        Query query = PropertyManagerRepo.class
                .getMethod("findRoleNameByUserIdAndPropertyIdAndActiveTrue", long.class, long.class)
                .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("pm.active");
    }

    @Test
    void sharedGuardTemplateDoesNotAllowUsingAnotherBusinessAreasDefinition() {
        var workspace = rentalOwnerWorkspace();
        TeamRoleDefinition definition = new TeamRoleDefinition();
        definition.setBusinessArea(TeamBusinessArea.ESTATE_MANAGEMENT);
        definition.setPermissionTemplate(TeamMembershipRole.GUARD);
        when(definitions.findByIdAndActiveTrue(12L)).thenReturn(Optional.of(definition));
        var exception = assertThrows(PMSCustomException.class, () -> service.invite(new TeamAccessModels.InviteRequest(
                "guard@example.com", 12L, TeamScopeType.SELECTED_RESOURCES, List.of(101L))));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.INVALID_ROLE);
        verify(invitations, never()).save(any());
        verifyNoInteractions(properties, propertyManagers);
        assertThat(workspace.getBusinessArea()).isEqualTo(TeamBusinessArea.LANDLORD);
    }

    @Test
    void sharedGuardInvitationStillRejectsPropertiesOutsideTheOwnersWorkspace() {
        rentalOwnerWorkspace();
        TeamRoleDefinition definition = new TeamRoleDefinition();
        definition.setBusinessArea(TeamBusinessArea.LANDLORD);
        definition.setPermissionTemplate(TeamMembershipRole.GUARD);
        when(definitions.findByIdAndActiveTrue(12L)).thenReturn(Optional.of(definition));
        when(memberships.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(8L)).thenReturn(List.of());
        Property unrelated = new Property();
        unrelated.setId(101L); unrelated.setActive(true); unrelated.setCreatedBy(999L);
        when(properties.findAllById(List.of(101L))).thenReturn(List.of(unrelated));
        var exception = assertThrows(PMSCustomException.class, () -> service.invite(new TeamAccessModels.InviteRequest(
                "guard@example.com", 12L, TeamScopeType.SELECTED_RESOURCES, List.of(101L))));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.PROPERTY_FORBIDDEN_ACCESS);
        verify(invitations, never()).save(any());
        verifyNoInteractions(propertyManagers);
    }

    private CustomerWorkspace rentalOwnerWorkspace() {
        Users owner = new Users(); owner.setId(42L); owner.setEmail("owner@example.com");
        CustomerWorkspace workspace = new CustomerWorkspace(); workspace.setId(8L);
        workspace.setOwnerUserId(42L); workspace.setBusinessArea(TeamBusinessArea.LANDLORD); workspace.setActive(true);
        when(users.getUserObject()).thenReturn(owner);
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        when(workspaces.findByOwnerUserIdAndBusinessAreaAndActiveTrue(42L, TeamBusinessArea.LANDLORD)).thenReturn(Optional.of(workspace));
        when(workspaces.findLockedByIdAndActiveTrue(8L)).thenReturn(Optional.of(workspace));
        return workspace;
    }

    @Test
    void nonAdministrativeInternalUserCannotReadTheTeamDirectory() {
        Users actor = new Users();
        actor.setId(21L);
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setMembershipRole(TeamMembershipRole.PROPERTY_MANAGER);
        when(users.getUserObject()).thenReturn(actor);
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_MANAGER);
        when(workspaceSelection.selectedMembership(21L)).thenReturn(Optional.of(membership));

        PMSCustomException exception = assertThrows(PMSCustomException.class, service::current);

        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.FORBIDDEN_ACCESS);
        verify(workspaces, never()).findById(anyLong());
    }

    @Test
    void scopedWorkspaceAdministratorSeesOnlyAssignedResponsibilityAreas() {
        Users actor = new Users();
        actor.setId(22L);
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(8L);
        membership.setMembershipRole(TeamMembershipRole.WORKSPACE_ADMIN);
        membership.setScopeType(TeamScopeType.SELECTED_RESOURCES);
        membership.setResourceIdsJson("[101]");
        membership.setStatus(TeamMembershipStatus.ACTIVE);
        CustomerWorkspace workspace = new CustomerWorkspace();
        workspace.setId(8L);
        workspace.setOwnerUserId(42L);
        workspace.setBusinessArea(TeamBusinessArea.LANDLORD);
        workspace.setName("Rental team");
        workspace.setActive(true);
        Property assigned = new Property();
        assigned.setId(101L);
        assigned.setName("Assigned property");
        Property outsideScope = new Property();
        outsideScope.setId(102L);
        outsideScope.setName("Outside property");
        when(users.getUserObject()).thenReturn(actor);
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_ADMIN);
        when(workspaceSelection.selectedMembership(22L)).thenReturn(Optional.of(membership));
        when(workspaces.findById(8L)).thenReturn(Optional.of(workspace));
        when(invitations.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(8L)).thenReturn(List.of());
        when(memberships.findByWorkspaceIdAndActiveTrueOrderByCreatedOnDesc(8L)).thenReturn(List.of());
        when(properties.findAllByCreatedByAndActiveTrue(42L)).thenReturn(List.of(assigned, outsideScope));

        TeamAccessModels.WorkspaceView result = service.current();

        assertThat(result.canGrantEntireWorkspace()).isFalse();
        assertThat(result.resources()).extracting("id").containsExactly(101L);
    }

    @Test
    void scopedWorkspaceAdministratorCannotExpandAnotherUsersResponsibilitiesToTheWholeWorkspace() {
        Users actor = new Users();
        actor.setId(22L);
        WorkspaceMembership administrator = new WorkspaceMembership();
        administrator.setWorkspaceId(8L);
        administrator.setMembershipRole(TeamMembershipRole.WORKSPACE_ADMIN);
        administrator.setScopeType(TeamScopeType.SELECTED_RESOURCES);
        administrator.setResourceIdsJson("[101]");
        administrator.setStatus(TeamMembershipStatus.ACTIVE);
        WorkspaceMembership target = new WorkspaceMembership();
        target.setId(91L);
        target.setWorkspaceId(8L);
        target.setMembershipRole(TeamMembershipRole.VIEWER);
        target.setScopeType(TeamScopeType.SELECTED_RESOURCES);
        target.setResourceIdsJson("[101]");
        target.setStatus(TeamMembershipStatus.ACTIVE);
        CustomerWorkspace workspace = new CustomerWorkspace();
        workspace.setId(8L);
        workspace.setOwnerUserId(42L);
        workspace.setBusinessArea(TeamBusinessArea.LANDLORD);
        workspace.setActive(true);
        when(users.getUserObject()).thenReturn(actor);
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_ADMIN);
        when(workspaceSelection.selectedMembership(22L)).thenReturn(Optional.of(administrator));
        when(workspaces.findById(8L)).thenReturn(Optional.of(workspace));
        when(memberships.findByIdAndWorkspaceIdAndActiveTrue(91L, 8L)).thenReturn(Optional.of(target));

        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.updateScope(
                91L, new TeamAccessModels.ScopeUpdate(TeamScopeType.ENTIRE_WORKSPACE, List.of())));

        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.PROPERTY_FORBIDDEN_ACCESS);
        verify(memberships, never()).save(target);
    }
}
