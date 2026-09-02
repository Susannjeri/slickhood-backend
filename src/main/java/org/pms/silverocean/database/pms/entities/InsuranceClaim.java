package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="pms_insurance_claim") @Getter @Setter @NoArgsConstructor
public class InsuranceClaim extends BaseCreatorEntity {
 @Column(nullable=false) private long policyId;
 @Column(nullable=false) private long customerUserId;
 @Column(nullable=false,unique=true,length=32) private String reference;
 @Column(nullable=false,length=24) private String status;
 @Column(nullable=false) private LocalDateTime incidentAt;
 @Column(length=300) private String incidentLocation;
 @Lob @Column(nullable=false) private String description;
 private BigDecimal estimatedAmount;
 private Long assignedAdviserId;
 @Column(length=120) private String insurerReference;
 @Lob private String resolutionNotes;
 private LocalDateTime submittedAt;
 private LocalDateTime closedAt;
 @Version private long version;
}
