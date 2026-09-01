package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_wealth_asset_type", indexes = @Index(name = "idx_wealth_asset_type_catalog", columnList = "active,displayOrder"))
@Getter @Setter @NoArgsConstructor
public class WealthAssetType extends BaseCreatorEntity {
    @Column(nullable=false,unique=true,length=40) private String code;
    @Column(nullable=false,length=100) private String label;
    @Column(length=500) private String description;
    @Column(nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean marketPricingAllowed;
}
