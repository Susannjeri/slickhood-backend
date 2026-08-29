package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Table(name = "pms_property_account", indexes = {
        @Index(name = "idx_property_account_unique_active", columnList = "accountId, propertyId, active"),
        @Index(name = "idx_property_id_active", columnList = "propertyId, active"),
        @Index(name = "idx_property_id_active", columnList = "createdBy"),
        @Index(name = "idx_property_accountId", columnList = "accountId"),
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAccount extends BaseCreatorEntity implements Auditable {
    private long accountId = 0;
    private long propertyId;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"accountId\":" + accountId + "," +
                "\"propertyId\":" + propertyId + "," +
                "\"active\":" + isActive() + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"" +
                "}";
    }
}
