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
@Table(name = "pms_sp_referee", indexes = {
        @Index(name = "idx_sp_referee_profileId", columnList = "profileId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Referee extends BaseCreatorEntity implements Auditable {
    private long profileId;
    private String name;
    private String contact;
    private String verificationStatus;
    private String adminNotes;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"profileId\":" + profileId + "," +
                "\"name\":\"" + name + "\"," +
                "\"verificationStatus\":\"" + verificationStatus + "\"" +
                "}";
    }
}
