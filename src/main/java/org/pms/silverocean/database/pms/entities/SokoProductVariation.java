package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "pms_soko_product_variation", indexes = {
        @Index(name = "idx_soko_product_variation_product", columnList = "productId,active")
})
@Getter @Setter @NoArgsConstructor
public class SokoProductVariation extends BaseCreatorEntity {
    private long productId;
    private String name;
    private String value;
    private BigDecimal priceAdjustment;
    private int stockQuantity;
}
