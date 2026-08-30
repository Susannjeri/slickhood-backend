package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity; import org.pms.silverocean.service.teamaccess.*; import java.time.LocalDateTime;

@Entity @Table(name="pms_workspace_invitation",indexes={@Index(name="idx_workspace_invite_workspace_status",columnList="workspaceId,status,active"),@Index(name="idx_workspace_invite_email",columnList="recipientEmail,status")},uniqueConstraints=@UniqueConstraint(name="uk_workspace_invite_token_hash",columnNames="tokenHash")) @Getter @Setter
public class WorkspaceInvitation extends BaseCreatorEntity implements Auditable {
    private long workspaceId; @Column(length=254,nullable=false) private String recipientEmail;
    private Long roleDefinitionId;
    @Enumerated(EnumType.STRING) @Column(length=40,nullable=false) private TeamMembershipRole membershipRole;
    @Enumerated(EnumType.STRING) @Column(length=30,nullable=false) private TeamScopeType scopeType;
    @Lob @Column(columnDefinition="TEXT") private String resourceIdsJson;
    @Column(length=64,nullable=false) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(length=30,nullable=false) private TeamMembershipStatus status;
    private LocalDateTime expiresAt; private LocalDateTime acceptedAt; private LocalDateTime lastSentAt; private int resendCount; private Long membershipId;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"workspaceId\":"+workspaceId+",\"recipientEmail\":\""+recipientEmail+"\",\"membershipRole\":\""+membershipRole+"\",\"scopeType\":\""+scopeType+"\",\"status\":\""+status+"\"}";}
}
