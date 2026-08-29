package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_sp_profile", indexes = {
        @Index(name = "idx_sp_profile_userId", columnList = "userId"),
        @Index(name = "idx_sp_profile_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProviderProfile extends BaseCreatorEntity implements Auditable {
    private long userId;
    private String businessName;
    private String status;
    private ZonedDateTime consentTimestamp;
    private String consentIpAddress;
    private Double latitude;
    private Double longitude;
    private String adminNotes;
    private Long paymentAccountId;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"userId\":" + userId + "," +
                "\"businessName\":\"" + businessName + "\"," +
                "\"status\":\"" + status + "\"," +
                "\"consentTimestamp\":\"" + consentTimestamp + "\"" +
                "}";
    }
}
