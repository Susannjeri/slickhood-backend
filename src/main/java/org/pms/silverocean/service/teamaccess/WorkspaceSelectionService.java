package org.pms.silverocean.service.teamaccess;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.CustomerWorkspaceRepo;
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.database.pms.entities.CustomerWorkspace;
import org.pms.silverocean.database.pms.entities.WorkspaceMembership;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkspaceSelectionService {
    public static final String HEADER = "X-Slickhood-Workspace";
    private final HttpServletRequest request;
    private final UserDao users;
    private final WorkspaceMembershipRepo memberships;
    private final CustomerWorkspaceRepo workspaces;

    @Transactional(readOnly = true)
    public Optional<WorkspaceMembership> selectedMembership(long userId) {
        Optional<TeamMembershipRole> membershipRole = TeamMembershipRole.fromPlatformRole(users.getActiveRole());
        if (membershipRole.isEmpty()) return Optional.empty();
        List<WorkspaceMembership> candidates = memberships
                .findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                        userId, membershipRole.get(), TeamMembershipStatus.ACTIVE);
        if (candidates.isEmpty()) return Optional.empty();
        Long requestedId = requestedWorkspaceId();
        if (requestedId == null) {
            if (candidates.size() == 1) return Optional.of(candidates.getFirst());
            throw new PMSCustomException(ResponseCode.WORKSPACE_SELECTION_REQUIRED);
        }
        return candidates.stream().filter(candidate -> candidate.getWorkspaceId() == requestedId).findFirst()
                .or(() -> { throw new PMSCustomException(ResponseCode.WORKSPACE_SELECTION_INVALID); });
    }

    @Transactional(readOnly = true)
    public List<WorkspaceOption> available() {
        Long userId = users.getUserId();
        if (userId == null) throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        Optional<TeamMembershipRole> membershipRole = TeamMembershipRole.fromPlatformRole(users.getActiveRole());
        if (membershipRole.isPresent()) {
            return memberships.findAllByUserIdAndMembershipRoleAndStatusAndActiveTrueOrderByCreatedOnDesc(
                            userId, membershipRole.get(), TeamMembershipStatus.ACTIVE).stream()
                    .map(member -> workspaces.findById(member.getWorkspaceId()).filter(CustomerWorkspace::isActive)
                            .map(workspace -> new WorkspaceOption(workspace.getId(), workspace.getName(),
                                    workspace.getBusinessArea(), false)).orElse(null))
                    .filter(java.util.Objects::nonNull).toList();
        }
        try {
            TeamBusinessArea area = TeamBusinessArea.fromOwnerRole(users.getActiveRole());
            return workspaces.findByOwnerUserIdAndBusinessAreaAndActiveTrue(userId, area)
                    .map(workspace -> List.of(new WorkspaceOption(workspace.getId(), workspace.getName(), area, true)))
                    .orElse(List.of());
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private Long requestedWorkspaceId() {
        String value = request.getHeader(HEADER);
        if (StringUtils.isBlank(value)) return null;
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException error) {
            throw new PMSCustomException(ResponseCode.WORKSPACE_SELECTION_INVALID);
        }
    }

    public record WorkspaceOption(long id, String name, TeamBusinessArea businessArea, boolean owner) {}
}
