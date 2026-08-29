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
@Table(name = "pms_soko_order_item", indexes = {
        @Index(name = "idx_soko_order_item_order", columnList = "orderId")
})
@Getter @Setter @NoArgsConstructor
public class SokoOrderItem extends BaseCreatorEntity {
    private long orderId;
    private long productId;
    private String productName;
    private String unit;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;
}
