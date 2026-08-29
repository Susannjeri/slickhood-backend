package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_valuation", indexes=@Index(name="idx_wealth_valuation_asset",columnList="assetId,valuationDate"))
@Getter @Setter @NoArgsConstructor
public class WealthValuation extends BaseCreatorEntity {
    @Column(nullable=false) private long assetId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false) private LocalDate valuationDate;
    @Column(nullable=false,length=60) private String source;
    @Column(length=1000) private String notes;
}
