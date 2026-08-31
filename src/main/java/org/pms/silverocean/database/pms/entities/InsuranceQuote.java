package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="pms_insurance_quote") @Getter @Setter @NoArgsConstructor
public class InsuranceQuote extends BaseCreatorEntity {
 @Column(nullable=false) private long caseId;
 @Column(nullable=false) private long companyId;
 @Column(length=80) private String quoteNumber;
 @Column(nullable=false,length=24) private String status;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false) private BigDecimal basePremium;
 @Column(nullable=false) private BigDecimal taxesLevies;
 @Column(nullable=false) private BigDecimal totalPremium;
 @Column(length=1000) private String excessDetails;
 @Lob private String coverageSummary;
 @Lob private String exclusions;
 @Column(nullable=false) private LocalDate validUntil;
 @Column(nullable=false) private long preparedBy;
 private Long approvedBy;
 private LocalDateTime approvedAt;
 @Version private long version;
}
