package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.ZonedDateTime;

/** Supplemental rider evidence. Never changes the user's common KYC case or account state. */
@Entity @Table(name="pms_soko_rider_credential") @Getter @Setter
public class SokoRiderCredential extends BaseCreatorEntity implements Auditable {
    private long userId;
    private String documentType;
    private String fileRef;
    private String contentType;
    private String sha256;
    private String status;
    private ZonedDateTime expiresAt;
    private ZonedDateTime reviewedAt;
    private Long reviewedBy;
    @Column(length=1000) private String reviewNotes;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"userId\":"+userId+",\"documentType\":\""+documentType+"\",\"status\":\""+status+"\"}";}
}
