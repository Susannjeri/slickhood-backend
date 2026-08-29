package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_privacy_request", indexes = {
        @Index(name = "idx_privacy_request_user", columnList = "userId,status,active"),
        @Index(name = "idx_privacy_request_due", columnList = "status,dueAt,active")
})
@Getter @Setter
public class PrivacyRequest extends BaseCreatorEntity implements Auditable {
    private long userId;
    private String requestType;
    private String status;
    private String reason;
    private ZonedDateTime dueAt;
    private boolean legalHold;
    private String retentionBasis;
    private String reviewerNotes;
    private String resultReference;
    private Long reviewedBy;
    private ZonedDateTime reviewedAt;

    @Override
    public String toAuditJSON() {
        return "{\"id\":" + getId() + ",\"userId\":" + userId +
                ",\"requestType\":\"" + requestType + "\",\"status\":\"" + status +
                "\",\"legalHold\":" + legalHold + "}";
    }
}
