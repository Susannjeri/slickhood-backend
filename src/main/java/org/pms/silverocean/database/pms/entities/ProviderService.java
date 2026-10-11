package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;
import org.pms.silverocean.service.sp.enums.PricingUnit;

import java.math.BigDecimal;

@Entity
@Table(name = "pms_sp_service", indexes = {
        @Index(name = "idx_sp_service_profileId", columnList = "profileId"),
        @Index(name = "idx_sp_service_categoryId", columnList = "categoryId"),
        @Index(name = "idx_sp_service_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProviderService extends BaseCreatorEntity implements Auditable {
    private long profileId;
    private long categoryId;
    private String categoryName;
    @jakarta.persistence.Column(length = 120)
    private String title;
    @jakarta.persistence.Column(length = 2000)
    private String description;
    private String tier;
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PricingUnit pricingUnit;
    private String status;

    @Override
    public String toAuditJSON() {
        var json = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        json.put("id", getId());
        json.put("profileId", profileId);
        json.put("categoryId", categoryId);
        json.put("categoryName", categoryName);
        json.put("title", title);
        json.put("status", status);
        return json.toString();
    }
}
