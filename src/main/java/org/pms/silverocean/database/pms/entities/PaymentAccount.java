package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

@Entity
@Table(name = "pms_payment_account", indexes = {
        @Index(name = "idx_payment_account_created_by",    columnList = "createdBy"),
        @Index(name = "idx_payment_account_category",      columnList = "category"),
        @Index(name = "idx_payment_account_channel",       columnList = "channel"),
        @Index(name = "idx_payment_account_category_active", columnList = "category, active"),
        @Index(name = "idx_payment_account_category_active_createdBy", columnList = "category, active, createdBy")
})
@Getter
@Setter
public class PaymentAccount extends BaseCreatorEntity implements Auditable {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentChannel channel;

    @Column(nullable = false)
    private String name;

    private boolean verified = false;

    @Override
    public String toAuditJSON() {
        return "{\"id\":" + getId() +
                ",\"uuid\":\"" + getUuid() + "\"" +
                ",\"name\":\"" + name + "\"" +
                ",\"category\":\"" + category + "\"" +
                ",\"channel\":\"" + channel + "\"" +
                ",\"verified\":" + verified +
                ",\"createdBy\":" + getCreatedBy() +
                ",\"active\":" + isActive() + "}";
    }
}
