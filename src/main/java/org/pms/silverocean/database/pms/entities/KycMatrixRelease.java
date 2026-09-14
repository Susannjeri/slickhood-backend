package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_kyc_matrix_release", indexes = @Index(name = "idx_kyc_matrix_release_status", columnList = "status,active"))
@Getter @Setter @NoArgsConstructor
public class KycMatrixRelease extends BaseCreatorEntity implements Auditable {
    private int versionNo;
    private String status;
    private String changeSummary;
    private ZonedDateTime publishedAt;
    private Long publishedBy;

    @Override public String toAuditJSON() {
        return "{\"id\":" + getId() + ",\"versionNo\":" + versionNo + ",\"status\":\"" + status + "\"}";
    }
}
