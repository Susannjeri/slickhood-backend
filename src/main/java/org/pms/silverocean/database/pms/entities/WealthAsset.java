package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pms_wealth_asset", indexes = {
        @Index(name = "idx_wealth_asset_owner", columnList = "ownerUserId, active"),
        @Index(name = "idx_wealth_asset_property", columnList = "propertyId")})
@Getter @Setter @NoArgsConstructor
public class WealthAsset extends BaseCreatorEntity {
    @Column(nullable = false) private long ownerUserId;
    private Long propertyId;
    @Column(nullable = false, length = 40) private String assetType;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 120) private String reference;
    @Column(length = 500) private String location;
    @Column(nullable = false, length = 3) private String currency;
    @Column(precision = 19, scale = 2) private BigDecimal acquisitionCost;
    private LocalDate acquisitionDate;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal currentValue;
    @Column(nullable = false) private LocalDate valuationDate;
    @Column(nullable = false, length = 30) private String status;
}
