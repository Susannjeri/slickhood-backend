package org.pms.silverocean.service.teamaccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.CustomerWorkspaceRepo;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceSelectionServiceTest {
    private MockHttpServletRequest request;
    private UserDao users;
    private WorkspaceMembershipRepo memberships;
    private WorkspaceSelectionService service;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        users = mock(UserDao.class);
        memberships = mock(WorkspaceMembershipRepo.class);
        service = new WorkspaceSelectionService(request, users, memberships, mock(CustomerWorkspaceRepo.class));
        when(users.getActiveRole()).thenReturn(PMSRole.PROPERTY_ACCOUNTANT);
    }

    @Test
    void automaticallySelectsTheOnlyActiveWorkspace() {
        WorkspaceMembership only = membership(11L);
        when(memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                7L, TeamMembershipRole.PROPERTY_ACCOUNTANT, TeamMembershipStatus.ACTIVE))
                .thenReturn(List.of(only));

        assertThat(service.selectedMembership(7L)).contains(only);
    }

    @Test
    void requiresExplicitWorkspaceWhenSameRoleExistsInMultipleWorkspaces() {
        when(memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                7L, TeamMembershipRole.PROPERTY_ACCOUNTANT, TeamMembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(11L), membership(12L)));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.selectedMembership(7L));

        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.WORKSPACE_SELECTION_REQUIRED);
    }

    @Test
    void selectsOnlyTheRequestedActiveMembership() {
        WorkspaceMembership selected = membership(12L);
        when(memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                7L, TeamMembershipRole.PROPERTY_ACCOUNTANT, TeamMembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(11L), selected));
        request.addHeader(WorkspaceSelectionService.HEADER, "12");

        assertThat(service.selectedMembership(7L)).contains(selected);
    }

    @Test
    void rejectsAWorkspaceOutsideTheUsersActiveMemberships() {
        when(memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                7L, TeamMembershipRole.PROPERTY_ACCOUNTANT, TeamMembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(11L), membership(12L)));
        request.addHeader(WorkspaceSelectionService.HEADER, "99");

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.selectedMembership(7L));

        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.WORKSPACE_SELECTION_INVALID);
    }

    private WorkspaceMembership membership(long workspaceId) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(7L);
        membership.setMembershipRole(TeamMembershipRole.PROPERTY_ACCOUNTANT);
        membership.setStatus(TeamMembershipStatus.ACTIVE);
        membership.setActive(true);
        return membership;
    }
}
