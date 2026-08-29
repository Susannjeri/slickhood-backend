package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_sp_tier", indexes = {
        @Index(name = "idx_sp_tier_name", columnList = "name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ServiceTier extends BaseCreatorEntity implements Auditable {
    private String name;
    private String description;
    private String requirements;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"description\":\"" + description + "\"" +
                "}";
    }
}
