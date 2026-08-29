package org.pms.silverocean.service.sp.wrappers;

import org.pms.silverocean.database.pms.entities.RiskScore;

import java.time.ZonedDateTime;

public record RiskScoreDTO(long serviceId, String label, int highlyRatedCompletedCount, ZonedDateTime computedAt) {
    public RiskScoreDTO(RiskScore r) {
        this(r.getServiceId(), r.getLabel(), r.getHighlyRatedCompletedCount(), r.getComputedAt());
    }
}
