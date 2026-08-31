package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.*;
@Entity @Table(name="pms_insurance_policy") @Getter @Setter @NoArgsConstructor
public class InsurancePolicy extends BaseCreatorEntity {
 @Column(nullable=false) private long caseId;
 @Column(nullable=false) private long quoteId;
 @Column(nullable=false) private long companyId;
 @Column(nullable=false) private long customerUserId;
 @Column(nullable=false,unique=true,length=120) private String policyNumber;
 @Column(nullable=false,length=24) private String status;
 @Column(nullable=false) private LocalDate startDate;
 @Column(nullable=false) private LocalDate endDate;
 @Column(nullable=false,length=24) private String renewalStatus;
 private LocalDateTime renewalContactedAt;
 private LocalDateTime renewalReminderSentAt;
 @Column(nullable=false) private long issuedBy;
 @Column(nullable=false) private LocalDateTime issuedAt;
 @Version private long version;
}
