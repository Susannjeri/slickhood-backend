package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseIDEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "pms_payment_account_property",
        indexes = {
                @Index(name = "idx_acc_prop_account_id",  columnList = "accountId"),
                @Index(name = "idx_acc_prop_key",         columnList = "accountId, propertyKey")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_acc_prop_account_key", columnNames = {"accountId", "propertyKey"})
        })
@Getter
@Setter
public class PaymentAccountProperty extends BaseIDEntity implements Auditable {

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String propertyKey;

    @Lob
    @Column(nullable = false)
    private byte[] value;

    private boolean encrypted;

    private LocalDateTime lastModifiedDate;

    @Override
    public String toAuditJSON() {
        return "{\"id\":" + getId() +
                ",\"uuid\":\"" + getUuid() + "\"" +
                ",\"accountId\":" + accountId +
                ",\"propertyKey\":\"" + propertyKey + "\"" +
                ",\"encrypted\":" + encrypted + "}";
    }
}
