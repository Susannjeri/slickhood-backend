package org.pms.silverocean.service.teamaccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.TeamRoleDefinitionRepo;
import org.pms.silverocean.database.pms.entities.TeamRoleDefinition;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamRoleDefinitionServiceTest {
    @Mock TeamRoleDefinitionRepo definitions;
    @Mock UserDao users;
    @Mock AuditLogService audit;
    @InjectMocks TeamRoleDefinitionService service;

    @BeforeEach void admin() { when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true); }

    @Test void superadminCanCreateParameterizedGuardTypeForEstateOnly() {
        when(definitions.findByCodeIgnoreCase("DAY_GUARD")).thenReturn(Optional.empty());
        when(users.getUserId()).thenReturn(1L);
        when(definitions.save(any())).thenAnswer(invocation -> { TeamRoleDefinition value = invocation.getArgument(0); value.setId(77L); return value; });
        var result = service.create(new TeamAccessModels.RoleDefinitionRequest(
                "DAY_GUARD", "Day shift guard", "Day shift access", TeamBusinessArea.ESTATE_MANAGEMENT, TeamMembershipRole.GUARD));
        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.permissionTemplate()).isEqualTo(TeamMembershipRole.GUARD);
        verify(audit).createAuditLog(any(TeamRoleDefinition.class), eq("team_role_definition_create"));
    }

    @Test void guardTemplateCannotBeExpandedIntoLandlordBusinessArea() {
        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.create(
                new TeamAccessModels.RoleDefinitionRequest("LAND_GUARD", "Guard", null, TeamBusinessArea.LANDLORD, TeamMembershipRole.GUARD)));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.INVALID_ROLE);
        verify(definitions, never()).save(any());
    }

    @Test void customerAdministratorCannotCreateUserTypes() {
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(false);
        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.create(
                new TeamAccessModels.RoleDefinitionRequest("VIEW_ONLY", "View only", null, TeamBusinessArea.LANDLORD, TeamMembershipRole.VIEWER)));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.FORBIDDEN_ACCESS);
        verify(definitions, never()).save(any());
    }
}
