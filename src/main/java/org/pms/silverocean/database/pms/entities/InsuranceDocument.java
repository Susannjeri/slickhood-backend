package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
@Entity @Table(name="pms_insurance_document") @Getter @Setter @NoArgsConstructor
public class InsuranceDocument extends BaseCreatorEntity {
 @Column(nullable=false) private long customerUserId;
 private Long caseId;
 private Long policyId;
 private Long claimId;
 @Column(nullable=false,length=40) private String category;
 @Column(nullable=false,length=255) private String displayName;
 @Column(nullable=false,length=800) private String fileRef;
 @Column(nullable=false,length=120) private String contentType;
 @Column(nullable=false) private long fileSize;
 @Column(nullable=false,length=64) private String checksumSha256;
 @Column(nullable=false) private long uploadedBy;
 @Column(nullable=false) private int versionNumber;
}
