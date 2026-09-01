package org.pms.silverocean.database.pms.entities;
import jakarta.persistence.*;
import lombok.*;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.time.*;
@Entity @Table(name="pms_insurance_renewal_reminder") @Getter @Setter @NoArgsConstructor
public class InsuranceRenewalReminder extends BaseCreatorEntity {
 @Column(nullable=false) private long policyId; @Column(nullable=false) private LocalDate policyEndDate; @Column(nullable=false) private int reminderDays; @Column(nullable=false) private LocalDateTime queuedAt;
}
