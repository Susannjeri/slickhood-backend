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
@Table(name = "pms_soko_store", indexes = {
        @Index(name = "idx_soko_store_owner", columnList = "ownerUserId,active"),
        @Index(name = "idx_soko_store_status", columnList = "status,active")
})
@Getter @Setter @NoArgsConstructor
public class SokoStore extends BaseCreatorEntity {
    private long ownerUserId;
    private String name;
    private String description;
    private String phoneNumber;
    private String address;
    private Double latitude;
    private Double longitude;
    private BigDecimal serviceRadiusKm;
    private String status;
    private boolean pickupEnabled;
    private boolean deliveryEnabled;
    private BigDecimal deliveryFee;
    private String currency;
    private Long paymentAccountId;
}
