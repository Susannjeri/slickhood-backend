package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
    @jakarta.persistence.Column(length = 1000)
    private String description;
    private String serviceArea;
    private String status;
    private ZonedDateTime consentTimestamp;
    private String consentIpAddress;
    private Double latitude;
    private Double longitude;
    @jakarta.persistence.Column(length = 1000)
    private String adminNotes;
    private Long reviewedByUserId;
    private ZonedDateTime reviewedAt;
    private Long paymentAccountId;

    @Version
    private long version;

    @Override
    public String toAuditJSON() {
        var json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put("id", getId());
        json.put("userId", userId);
        json.put("businessName", businessName);
        json.put("status", status);
        json.put("consentTimestamp", consentTimestamp == null ? null : consentTimestamp.toString());
        json.put("version", version);
        return json.toString();
    }
}
