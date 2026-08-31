package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="pms_insurance_case") @Getter @Setter @NoArgsConstructor
public class InsuranceCase extends BaseCreatorEntity {
 @Column(nullable=false) private long agencyId;
 @Column(nullable=false) private long customerUserId;
 @Column(nullable=false,unique=true,length=32) private String reference;
 @Column(nullable=false,length=40) private String productCode;
 @Column(nullable=false,length=32) private String status;
 @Column(nullable=false,length=160) private String fullName;
 @Column(nullable=false,length=254) private String email;
 @Column(nullable=false,length=40) private String phone;
 @Column(nullable=false,length=32) private String subjectType;
 @Column(nullable=false,length=1000) private String subjectDescription;
 private BigDecimal sumInsured;
 @Column(nullable=false,length=3) private String currency;
 private LocalDate coverStartDate;
 @Lob private String riskDetails;
 @Column(nullable=false) private LocalDateTime consentAt;
 private Long assignedAdviserId;
 private LocalDateTime submittedAt;
 private LocalDateTime quotedAt;
 private LocalDateTime selectedAt;
 private LocalDateTime paymentReminderSentAt;
 private Long selectedQuoteId;
 @Version private long version;
}
