package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_soko_product_image", indexes = {
        @Index(name = "idx_soko_product_image_product", columnList = "productId,active,displayOrder")
})
@Getter @Setter @NoArgsConstructor
public class SokoProductImage extends BaseCreatorEntity {
    private long productId;
    private String fileRef;
    private String contentType;
    private long fileSize;
    private int displayOrder;
}
