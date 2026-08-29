package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name="pms_soko_rider",indexes={
        @Index(name="idx_soko_rider_store",columnList="storeId,active"),
        @Index(name="idx_soko_rider_availability",columnList="storeId,availability,active")
})
@Getter @Setter @NoArgsConstructor
public class SokoRider extends BaseCreatorEntity {
    private long storeId;
    private String riderType;
    private String displayName;
    private String phoneNumber;
    private String email;
    private String vehicleType;
    private String vehiclePlate;
    private String availability;
    private String status;
    private boolean verified;
    private int completedDeliveries;
    private String notes;
}
