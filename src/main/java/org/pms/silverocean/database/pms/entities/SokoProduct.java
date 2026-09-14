package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_soko_product", indexes = {
        @Index(name = "idx_soko_product_store", columnList = "storeId,active"),
        @Index(name = "idx_soko_product_catalog", columnList = "status,category,active")
})
@Getter @Setter @NoArgsConstructor
public class SokoProduct extends BaseCreatorEntity implements Auditable {
    private long storeId;
    private String name;
    private String description;
    private String category;
    private String unit;
    private BigDecimal price;
    private String currency;
    private int stockQuantity;
    private String imageUrl;
    private String status;
    @jakarta.persistence.Column(columnDefinition="TEXT")
    private String variationsJson;
    private ZonedDateTime moderatedAt;
    private Long moderatedByUserId;
    @jakarta.persistence.Column(length=1000) private String moderationReason;
    @Override public String toAuditJSON(){return "{\"id\":"+getId()+",\"storeId\":"+storeId+",\"status\":\""+status+"\",\"stockQuantity\":"+stockQuantity+"}";}
}
