package org.pms.silverocean.service.teamaccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest @EnumSource(TeamBusinessArea.class)
    void superadminCanCreateSharedSecurityTypesInEveryBusinessArea(TeamBusinessArea area) {
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        when(definitions.findByCodeIgnoreCase("DAY_GUARD")).thenReturn(Optional.empty());
        when(users.getUserId()).thenReturn(1L);
        when(definitions.save(any())).thenAnswer(invocation -> { TeamRoleDefinition value = invocation.getArgument(0); value.setId(77L); return value; });
        for (var role : new TeamMembershipRole[]{TeamMembershipRole.GUARD, TeamMembershipRole.SECURITY_SUPERVISOR}) {
            var result = service.create(new TeamAccessModels.RoleDefinitionRequest(
                    "DAY_GUARD", role.displayName(), "Day shift access", area, role));
            assertThat(result.id()).isEqualTo(77L);
            assertThat(result.permissionTemplate()).isEqualTo(role);
            assertThat(result.businessArea()).isEqualTo(area);
        }
        verify(audit, times(2)).createAuditLog(any(TeamRoleDefinition.class), eq("team_role_definition_create"));
    }

    @Test void specialistTemplateCannotBeExpandedIntoAnotherBusinessArea() {
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.create(
                new TeamAccessModels.RoleDefinitionRequest("LAND_AGENT", "Agent", null, TeamBusinessArea.LANDLORD, TeamMembershipRole.LISTING_AGENT)));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.INVALID_ROLE);
        verify(definitions, never()).save(any());
    }

    @Test void customerAdministratorCannotCreateUserTypes() {
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_ADMIN);
        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.create(
                new TeamAccessModels.RoleDefinitionRequest("VIEW_ONLY", "View only", null, TeamBusinessArea.LANDLORD, TeamMembershipRole.VIEWER)));
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.FORBIDDEN_ACCESS);
        verify(definitions, never()).save(any());
    }

    @Test void dormantSuperadminRoleDoesNotAuthorizePlatformRoleChanges() {
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);

        PMSCustomException exception = assertThrows(PMSCustomException.class, service::list);

        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.FORBIDDEN_ACCESS);
        verifyNoInteractions(definitions);
    }

    @Test void everyCustomerTeamTemplateMapsToAnEmployeeRole() {
        assertThat(TeamMembershipRole.values())
                .allSatisfy(role -> assertThat(role.platformRole().isCustomerEmployeeRole()).isTrue());
    }

    @Test void catalogueMatchesTheEnforcedMatrixAndKeepsSpecialistsRestricted() {
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        assertThat(service.templates()).hasSize(TeamMembershipRole.values().length).allSatisfy(template -> {
            for (var area : TeamBusinessArea.values()) {
                assertThat(template.businessAreas().contains(area)).isEqualTo(template.permissionTemplate().allowedFor(area));
            }
        });
        for (var role : new TeamMembershipRole[]{TeamMembershipRole.GUARD, TeamMembershipRole.SECURITY_SUPERVISOR,
                TeamMembershipRole.WORKSPACE_ADMIN, TeamMembershipRole.PROPERTY_ACCOUNTANT, TeamMembershipRole.VIEWER}) {
            assertThat(service.templates().stream().filter(template -> template.permissionTemplate() == role).findFirst().orElseThrow().businessAreas())
                    .containsExactly(TeamBusinessArea.values());
        }
        assertThat(TeamMembershipRole.GUARD.platformRole()).isEqualTo(PMSRole.GUARD);
        assertThat(TeamMembershipRole.GUARD.privilegeLevel()).isEqualTo(20);
        assertThat(TeamMembershipRole.SECURITY_SUPERVISOR.platformRole()).isEqualTo(PMSRole.SECURITY_SUPERVISOR);
        assertThat(TeamMembershipRole.SECURITY_SUPERVISOR.privilegeLevel()).isEqualTo(40);
        assertThat(TeamMembershipRole.LEASING_OFFICER.allowedFor(TeamBusinessArea.PROPERTY_SALE_MANAGEMENT)).isFalse();
        assertThat(TeamMembershipRole.ESTATE_OPERATIONS_MANAGER.allowedFor(TeamBusinessArea.LANDLORD)).isFalse();
        verifyNoInteractions(definitions, audit);
    }

    @Test void customerCannotReadPlatformTemplateAdministration() {
        when(users.getActiveRole()).thenReturn(PMSRole.WORKSPACE_ADMIN);
        assertThat(assertThrows(PMSCustomException.class, service::templates).getResponseCode()).isEqualTo(ResponseCode.FORBIDDEN_ACCESS);
        verifyNoInteractions(definitions, audit);
    }
}
