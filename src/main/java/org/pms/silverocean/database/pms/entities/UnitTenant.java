package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

@Table(name = "pms_unit_tenant", indexes = {
        @Index(name = "idx_unit_tenant_userId", columnList = "userId"),
        @Index(name = "idx_unit_tenant_inviteId", columnList = "inviteId"),
        @Index(name = "idx_unit_tenant_active", columnList = "active"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnitTenant extends BaseActiveEntity implements Auditable {
    private long inviteId;
    private long userId;
    private long unitId;

    private boolean leaseAccepted;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"userId\":" + userId + "," +
                "\"unitId\":" + unitId + "," +
                "\"inviteId\":" + inviteId + "," +
                "\"leaseAccepted\":" + leaseAccepted + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\", " +
                "\"active\":" + isActive() +
                "}";
    }
}