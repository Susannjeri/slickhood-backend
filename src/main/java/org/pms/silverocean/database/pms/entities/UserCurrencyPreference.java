package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

@Entity
@Table(name = "pms_user_currency_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_currency_preference_user", columnNames = "user_id"))
@Getter @Setter
public class UserCurrencyPreference extends BaseCreatorEntity implements Auditable {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;
    @Column(name = "enabled_currencies", nullable = false, length = 1000)
    private String enabledCurrencies;
    @Version
    private long version;

    @Override public String toAuditJSON() {
        var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        node.put("userId", userId);
        node.put("defaultCurrency", defaultCurrency);
        node.put("enabledCurrencies", enabledCurrencies);
        node.put("version", version);
        return node.toString();
    }
}
