package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

@Table(name = "pms_user_role")
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole extends BaseIDEntity implements Auditable {
    private long userId;
    private long roleId;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"userId\":" + userId + "," +
                "\"roleId\":" + roleId + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"" +
                "}";
    }
}
