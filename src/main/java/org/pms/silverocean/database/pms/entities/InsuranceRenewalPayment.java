package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="pms_insurance_renewal_payment") @Getter @Setter @NoArgsConstructor
public class InsuranceRenewalPayment extends BaseCreatorEntity {
 @Column(nullable=false) private long policyId; @Column(nullable=false) private long renewalOfferId; @Column(nullable=false) private long paymentConfigurationId;
 @Column(nullable=false) private BigDecimal amount; @Column(nullable=false,length=3) private String currency; @Column(nullable=false,length=120) private String paymentReference;
 @Column(nullable=false) private LocalDateTime paidAt; @Column(nullable=false,length=24) private String status;
 @Column(length=800) private String proofFileRef; @Column(length=120) private String proofContentType; private Long proofFileSize; @Column(length=64) private String proofChecksum;
 private Long verifiedBy; private LocalDateTime verifiedAt; @Column(length=120) private String remittanceReference; private LocalDateTime remittedAt; @Column(length=500) private String rejectionReason;
 @Version private long version;
}
