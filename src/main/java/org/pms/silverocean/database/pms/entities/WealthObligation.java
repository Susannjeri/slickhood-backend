package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_obligation",indexes=@Index(name="idx_wealth_obligation_due",columnList="assetId,dueDate,status"))
@Getter @Setter @NoArgsConstructor
public class WealthObligation extends BaseCreatorEntity {
    @Column(nullable=false) private long assetId;
    @Column(nullable=false,length=40) private String obligationType;
    @Column(nullable=false,length=160) private String title;
    private LocalDate effectiveDate;
    private LocalDate dueDate;
    private LocalDate expiryDate;
    @Column(precision=19,scale=2) private BigDecimal amount;
    @Column(length=3) private String currency;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private int reminderDays;
    @Column(length=500) private String notes;
}
