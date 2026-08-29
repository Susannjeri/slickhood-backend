package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="pms_wealth_cash_flow",indexes=@Index(name="idx_wealth_cash_asset_date",columnList="assetId,entryDate"))
@Getter @Setter @NoArgsConstructor
public class WealthCashFlow extends BaseCreatorEntity {
    @Column(nullable=false) private long assetId;
    @Column(nullable=false,length=20) private String flowType;
    @Column(nullable=false,length=60) private String category;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
    @Column(nullable=false) private LocalDate entryDate;
    @Column(length=500) private String description;
    private boolean recurring;
}
