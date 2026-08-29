package org.pms.silverocean.database.pms.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.pms.silverocean.database.pms.entities.base.BaseCreatorEntity;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pms_sp_risk_score", indexes = {
        @Index(name = "idx_sp_risk_serviceId", columnList = "serviceId"),
        @Index(name = "idx_sp_risk_label", columnList = "label")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RiskScore extends BaseCreatorEntity implements Auditable {
    private long serviceId;
    private String label;
    private int highlyRatedCompletedCount;
    private String factorsJson;
    private ZonedDateTime computedAt;

    @Override
    public String toAuditJSON() {
        return "{" +
                "\"id\":" + getId() + "," +
                "\"serviceId\":" + serviceId + "," +
                "\"label\":\"" + label + "\"," +
                "\"highlyRatedCompletedCount\":" + highlyRatedCompletedCount + "," +
                "\"computedAt\":\"" + computedAt + "\"" +
                "}";
    }
}
