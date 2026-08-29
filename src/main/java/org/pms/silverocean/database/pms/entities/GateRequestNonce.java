package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_gate_request_nonce", uniqueConstraints =
        @UniqueConstraint(name = "uk_gate_nonce_device", columnNames = {"deviceId", "nonce"}),
        indexes = @Index(name = "idx_gate_nonce_expiry", columnList = "expiresAt"))
@Getter @Setter @NoArgsConstructor
public class GateRequestNonce extends BaseIDEntity {
    private long deviceId;
    private String nonce;
    private ZonedDateTime expiresAt;
}
