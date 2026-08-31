package org.pms.silverocean.service.teamaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.notification.NotificationService;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamAccessHardeningTest {
    private CustomerWorkspaceRepo workspaces;
    private WorkspaceInvitationRepo invitations;
    private WorkspaceMembershipRepo memberships;
    private PropertyRepo properties;
    private PropertyManagerRepo propertyManagers;
    private TeamAccessService service;

    @BeforeEach
    void setUp() {
        workspaces = mock(CustomerWorkspaceRepo.class);
        invitations = mock(WorkspaceInvitationRepo.class);
        memberships = mock(WorkspaceMembershipRepo.class);
        properties = mock(PropertyRepo.class);
        propertyManagers = mock(PropertyManagerRepo.class);
        service = new TeamAccessService(workspaces, invitations, memberships, mock(TeamRoleDefinitionRepo.class),
                properties, propertyManagers, mock(UserSubscriptionRepo.class), mock(SubscriptionPlanRepo.class),
                mock(PlanQuotaRepo.class), mock(RoleRepo.class), mock(UserRoleRepo.class), mock(UserDao.class),
                mock(ConfigService.class), mock(NotificationService.class), mock(I18NService.class),
                mock(AuditLogService.class), new ObjectMapper());
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
}
