package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.Entity;import jakarta.persistence.Table;import lombok.Getter;import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
@Entity @Table(name="pms_sale_evidence_attachment") @Getter @Setter
public class SaleEvidenceAttachment extends BaseCreatorEntity {
 private long saleId; private String category; private String displayName; private String fileRef;
 private String contentType; private long fileSize; private String checksumSha256; private long uploadedByUserId;
}
