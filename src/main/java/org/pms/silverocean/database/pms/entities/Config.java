package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

import java.time.LocalDateTime;

@Table(name = "pms_config")
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Config extends BaseIDEntity implements Auditable {
    private String name;
    @Lob
    private byte[] stringValue;
    private int intValue;
    private boolean encrypted;
    private LocalDateTime updatedOn = LocalDateTime.now();




    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"name\":\"" + name + "\"," +
                "\"stringValue\":\"" + (encrypted ? "*****" : new String(stringValue)) + "\"," +
                "\"intValue\":" + intValue + "," +
                "\"updatedOn\":\"" + updatedOn + "\""+
                "}";
    }
}
