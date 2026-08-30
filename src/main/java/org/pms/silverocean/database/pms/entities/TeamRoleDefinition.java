package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.teamaccess.TeamBusinessArea;
import org.pms.silverocean.service.teamaccess.TeamMembershipRole;

@Entity
@Table(name = "pms_team_role_definition",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_role_definition_code", columnNames = "code"),
        indexes = @Index(name = "idx_team_role_definition_area", columnList = "businessArea,active"))
@Getter @Setter
public class TeamRoleDefinition extends BaseCreatorEntity implements Auditable {
    @Column(length = 80, nullable = false) private String code;
    @Column(length = 120, nullable = false) private String displayName;
    @Column(length = 300) private String description;
    @Enumerated(EnumType.STRING) @Column(length = 40, nullable = false) private TeamBusinessArea businessArea;
    @Enumerated(EnumType.STRING) @Column(length = 40, nullable = false) private TeamMembershipRole permissionTemplate;

    @Override public String toAuditJSON() {
        return "{\"id\":" + getId() + ",\"code\":\"" + code + "\",\"displayName\":\"" + displayName
                + "\",\"businessArea\":\"" + businessArea + "\",\"permissionTemplate\":\"" + permissionTemplate
                + "\",\"active\":" + isActive() + "}";
    }
}
