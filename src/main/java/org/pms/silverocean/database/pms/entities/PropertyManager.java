package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseActiveEntity;

@Table(name = "pms_property_manager", indexes = {
        @Index(name = "idx_property_manager_userId", columnList = "userId"),
        @Index(name = "idx_property_manager_userId_role_property", columnList = "userId, roleName, propertyId"),
        @Index(name = "idx_property_manager_userId_property", columnList = "propertyId, userId"),
        @Index(name = "idx_property_manager_userId_active_property", columnList = "userId, active, propertyId"),
        @Index(name = "idx_property_manager_property_active", columnList = "propertyId, active"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyManager extends BaseActiveEntity implements Auditable {
    private long inviteId;
    @Column(name = "user_id", nullable = false)
    private long userId;
    @Column(name = "property_id", nullable = false)
    private long propertyId;
    private String roleName;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"userId\":" + userId + "," +
                "\"propertyId\":" + propertyId + "," +
                "\"roleName\":\"" + roleName + "\"," +
                "\"inviteId\":" + inviteId + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\", " +
                "\"active\":" + isActive() +
                "}";
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"userId\":" + userId + "," +
                "\"propertyId\":" + propertyId + "," +
                "\"roleName\":\"" + roleName + "\"," +
                "\"inviteId\":" + inviteId + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\", " +
                "\"active\":" + isActive() +
                "}";
    }
}
