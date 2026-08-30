package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity; import org.pms.silverocean.service.teamaccess.*; import java.time.LocalDateTime;

@Entity @Table(name="pms_workspace_membership",indexes={@Index(name="idx_workspace_membership_workspace_status",columnList="workspaceId,status,active"),@Index(name="idx_workspace_membership_user",columnList="userId,status,active")},uniqueConstraints=@UniqueConstraint(name="uk_workspace_membership_user",columnNames={"workspaceId","userId"})) @Getter @Setter
public class WorkspaceMembership extends BaseCreatorEntity implements Auditable {
    private long workspaceId; private long userId; @Column(length=254,nullable=false) private String memberEmail;
    private Long roleDefinitionId;
    @Enumerated(EnumType.STRING) @Column(length=40,nullable=false) private TeamMembershipRole membershipRole;
    @Enumerated(EnumType.STRING) @Column(length=30,nullable=false) private TeamScopeType scopeType;
    @Lob @Column(columnDefinition="TEXT") private String resourceIdsJson;
    @Enumerated(EnumType.STRING) @Column(length=30,nullable=false) private TeamMembershipStatus status;
    private LocalDateTime acceptedAt; private LocalDateTime activatedAt; private LocalDateTime suspendedAt; private LocalDateTime revokedAt;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"workspaceId\":"+workspaceId+",\"userId\":"+userId+",\"memberEmail\":\""+memberEmail+"\",\"membershipRole\":\""+membershipRole+"\",\"scopeType\":\""+scopeType+"\",\"status\":\""+status+"\"}";}
}
