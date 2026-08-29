package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_liability",indexes=@Index(name="idx_wealth_liability_asset",columnList="assetId,active"))
@Getter @Setter @NoArgsConstructor
public class WealthLiability extends BaseCreatorEntity {
    @Column(nullable=false) private long assetId;
    @Column(nullable=false,length=100) private String lender;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal originalPrincipal;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal outstandingPrincipal;
    @Column(precision=8,scale=4) private BigDecimal annualInterestRate;
    @Column(precision=19,scale=2) private BigDecimal monthlyPayment;
    private LocalDate startDate;
    private LocalDate maturityDate;
}
