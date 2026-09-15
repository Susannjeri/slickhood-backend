package org.pms.silverocean.service.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.CustomerWorkspaceRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.teamaccess.*;
import org.springframework.stereotype.Service;

import java.util.List;

/** An additional data restriction, never a role grant or an alternative authorisation path. */
@Service
@RequiredArgsConstructor
public class FinancialReportScopeService {
    private final UserDao users;
    private final WorkspaceSelectionService selection;
    private final CustomerWorkspaceRepo workspaces;
    private final PropertyRepo properties;
    private final ObjectMapper mapper;

    public Scope resolve() {
        PMSRole role = users.getActiveRole();
        Long userId = users.getUserId();
        if (userId == null || role == null) throw denied();
        if (role == PMSRole.SUPER_ADMIN || role == PMSRole.FINANCE) return Scope.personal();
        if (role.isCustomerEmployeeRole()) {
            var member = selection.selectedMembership(userId).orElseThrow(this::denied);
            var workspace = workspaces.findById(member.getWorkspaceId()).filter(CustomerWorkspace::isActive)
                    .orElseThrow(this::denied);
            if (!member.isActive() || member.getStatus() != TeamMembershipStatus.ACTIVE
                    || member.getUserId() != userId || member.getId() == null || member.getId() <= 0
                    || member.getMembershipRole() == null || member.getMembershipRole().platformRole() != role
                    || !member.getMembershipRole().allowedFor(workspace.getBusinessArea())) throw denied();
            List<Long> allowed = properties.findFinancialReportPropertyIds(workspace.getOwnerUserId(),
                    userId, role.name(), -member.getId());
            if (member.getScopeType() == TeamScopeType.SELECTED_RESOURCES) {
                try {
                    List<Long> selected = mapper.readValue(member.getResourceIdsJson(), new TypeReference<List<Long>>() {});
                    if (selected == null || selected.stream().anyMatch(id -> id == null || id <= 0)) throw denied();
                    allowed = allowed.stream().filter(selected::contains).toList();
                } catch (PMSCustomException error) {
                    throw error;
                } catch (Exception error) {
                    throw denied();
                }
            } else if (member.getScopeType() != TeamScopeType.ENTIRE_WORKSPACE) throw denied();
            return new Scope(true, nonEmpty(allowed), -member.getId(), role.name());
        }
        try {
            TeamBusinessArea.fromOwnerRole(role);
            // Properties can deliberately be shared across an owner's business areas. Never infer
            // financial authority from the property's legacy single-category managementMode.
            return new Scope(true, nonEmpty(properties.findFinancialReportPropertyIds(userId, userId, role.name(), null)),
                    null, role.name());
        } catch (IllegalArgumentException notWorkspaceOwner) {
            return Scope.personal();
        }
    }

    private List<Long> nonEmpty(List<Long> ids) { return ids.isEmpty() ? List.of(-1L) : List.copyOf(ids); }
    private PMSCustomException denied() { return new PMSCustomException(ResponseCode.WORKSPACE_SELECTION_INVALID); }

    public record Scope(boolean restricted, List<Long> propertyIds, Long assignmentId, String roleName) {
        public static Scope personal() { return new Scope(false, List.of(-1L), null, ""); }
    }
}
