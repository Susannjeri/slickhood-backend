package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="pms_insurance_renewal_offer") @Getter @Setter @NoArgsConstructor
public class InsuranceRenewalOffer extends BaseCreatorEntity {
 @Column(nullable=false) private long policyId;
 @Column(nullable=false,length=80) private String quoteNumber;
 @Column(nullable=false,length=3) private String currency;
 @Column(nullable=false) private BigDecimal basePremium;
 @Column(nullable=false) private BigDecimal taxesLevies;
 @Column(nullable=false) private BigDecimal totalPremium;
 @Column(nullable=false,length=12000) private String coverageSummary;
 @Column(length=12000) private String exclusions;
 @Column(nullable=false) private LocalDate validUntil;
 @Column(nullable=false) private LocalDate coverStartDate;
 @Column(nullable=false) private LocalDate coverEndDate;
 @Column(nullable=false,length=24) private String status;
 private Long approvedBy; private LocalDateTime approvedAt; private LocalDateTime acceptedAt; private LocalDateTime completedAt;
 @Version private long version;
}
