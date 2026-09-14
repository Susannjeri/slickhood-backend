package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_kyc_matrix_requirement", indexes = @Index(name = "idx_kyc_matrix_requirement_lookup", columnList = "releaseId,scopeType,scopeKey,active"))
@Getter @Setter @NoArgsConstructor
public class KycMatrixRequirement extends BaseCreatorEntity implements Auditable {
    private long releaseId;
    private String scopeType;
    private String scopeKey;
    private String scopeLabel;
    private String requirementCode;
    private String requirementLabel;
    private String obligation;
    private String profileScope;
    private String acceptedDocumentTypes;
    private String conditionDescription;
    private Integer validityDays;
    private Integer renewalLeadDays;

    @Override public String toAuditJSON() {
        return "{\"id\":" + getId() + ",\"releaseId\":" + releaseId + ",\"scopeType\":\"" + scopeType
                + "\",\"scopeKey\":\"" + scopeKey + "\",\"requirementCode\":\"" + requirementCode
                + "\",\"obligation\":\"" + obligation + "\",\"active\":" + isActive() + "}";
    }
}
