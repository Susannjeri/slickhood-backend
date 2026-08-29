package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_gate_device", indexes = {
        @Index(name = "idx_gate_device_code", columnList = "deviceCode", unique = true),
        @Index(name = "idx_gate_device_property", columnList = "propertyId,active")
})
@Getter @Setter @NoArgsConstructor
public class GateDevice extends BaseCreatorEntity {
    private String deviceCode;
    private long propertyId;
    private String displayName;
    private String gateName;
    private String laneName;
    private String publicKey;
    private boolean enabled;
    private ZonedDateTime lastSeenAt;
}
