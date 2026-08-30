package org.pms.silverocean.service.teamaccess;

import jakarta.validation.constraints.*;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import java.time.LocalDateTime;
import java.util.List;

public final class TeamAccessModels {
    private TeamAccessModels() {}
    public record InviteRequest(@NotBlank @Email @Size(max=254) String email,
                                @NotNull @Positive Long roleDefinitionId,
                                @NotNull TeamScopeType scopeType,
                                @Size(max=500) List<@Positive Long> resourceIds) {}
    public record ScopeUpdate(@NotNull TeamScopeType scopeType,
                              @Size(max=500) List<@Positive Long> resourceIds) {}
    public record RoleOption(long id, String code, String name, TeamMembershipRole permissionTemplate) {}

    public record RoleDefinitionRequest(@NotBlank @Pattern(regexp="[A-Z0-9_]{3,80}") String code,
                                        @NotBlank @Size(max=120) String displayName,
                                        @Size(max=300) String description,
                                        @NotNull TeamBusinessArea businessArea,
                                        @NotNull TeamMembershipRole permissionTemplate) {}

    public record RoleDefinitionView(long id, String code, String displayName, String description,
                                     TeamBusinessArea businessArea, TeamMembershipRole permissionTemplate,
                                     boolean active) {}
    public record InvitationView(long id, String email, TeamMembershipRole role, String roleName,
                                 TeamScopeType scopeType, List<Long> resourceIds, TeamMembershipStatus status,
                                 LocalDateTime expiresAt, int resendCount) {}
    public record MemberView(long id, long userId, String email, String name, TeamMembershipRole role,
                             String roleName, TeamScopeType scopeType, List<Long> resourceIds,
                             TeamMembershipStatus status, LocalDateTime acceptedAt, LocalDateTime activatedAt) {}
    public record WorkspaceView(long id, String name, TeamBusinessArea businessArea, boolean owner,
                                long seatLimit, long seatsUsed, List<RoleOption> roles,
                                List<IdNameDescDTO> resources, List<InvitationView> invitations,
                                List<MemberView> members) {}
    public record InviteInspection(String maskedEmail, String workspaceName, String businessArea,
                                   String roleName, LocalDateTime expiresAt, TeamMembershipStatus status) {}
}
