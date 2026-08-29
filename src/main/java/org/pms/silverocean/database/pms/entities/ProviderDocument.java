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
@Table(name = "pms_sp_document", indexes = {
        @Index(name = "idx_sp_document_serviceId", columnList = "serviceId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProviderDocument extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    private String documentType;
    private String fileRef;
    private ZonedDateTime expiryDate;
    private String verificationStatus;
    private String adminNotes;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"serviceId\":" + serviceId + "," +
                "\"documentType\":\"" + documentType + "\"," +
                "\"verificationStatus\":\"" + verificationStatus + "\"" +
                "}";
    }
}
