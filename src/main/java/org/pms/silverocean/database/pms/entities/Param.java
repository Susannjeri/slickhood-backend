package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Table(name = "pms_params", indexes = {
        @Index(name = "idx_param_created_by", columnList = "createdBy, active"),
        @Index(name = "idx_param_name", columnList = "name")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Param extends BaseCreatorEntity implements Auditable {
    private String name;
    private String type;
    @Lob
    private byte[] value;
    private boolean verified = false;
    private boolean encrypted = false;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createdBy", insertable = false, updatable = false)
    private Users creator;
    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"type\":\"" + type + "\"," +
                "\"WAValue\":\"" + (encrypted ? "*****" : new String(value)) + "\"," +
                "\"verified\":" + verified + "," +
                "\"active\":" + isActive() + "," +
                "\"createdBy\":" + getCreatedBy() + "," +
                "\"createdOn\":\"" + getCreatedOn() + "\"," +
                "\"updatedOn\":\"" + getLastModifiedDate() + "\"" +
                "}";
    }
}
