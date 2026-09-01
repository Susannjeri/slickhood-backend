package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name="pms_wealth_market_quote",uniqueConstraints=@UniqueConstraint(name="uk_wealth_market_quote_instrument",columnNames={"exchangeCode","instrumentSymbol"}),indexes=@Index(name="idx_wealth_market_quote_asof",columnList="quoteAsOf"))
@Getter @Setter @NoArgsConstructor
public class WealthMarketQuote extends BaseCreatorEntity {
    @Column(nullable=false,length=20) private String exchangeCode;
    @Column(nullable=false,length=40) private String instrumentSymbol;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,precision=19,scale=6) private BigDecimal price;
    @Column(precision=19,scale=6) private BigDecimal previousClose;
    @Column(precision=19,scale=6) private BigDecimal changeAmount;
    @Column(precision=12,scale=6) private BigDecimal changePercent;
    @Column(nullable=false,length=40) private String provider;
    @Column(nullable=false) private ZonedDateTime quoteAsOf;
    @Column(nullable=false,length=20) private String freshness;
}
