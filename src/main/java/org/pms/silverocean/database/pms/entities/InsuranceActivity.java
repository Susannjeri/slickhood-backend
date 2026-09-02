package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
@Entity @Table(name="pms_insurance_activity") @Getter @Setter @NoArgsConstructor
public class InsuranceActivity extends BaseCreatorEntity {
 private Long caseId;
 private Long claimId;
 @Column(nullable=false,length=40) private String eventType;
 @Column(length=32) private String fromStatus;
 @Column(length=32) private String toStatus;
 @Column(length=1000) private String note;
 @Column(nullable=false) private long actorUserId;
}
