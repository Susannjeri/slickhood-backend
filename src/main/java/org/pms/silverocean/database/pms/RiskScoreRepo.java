package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RiskScoreRepo extends JpaRepository<RiskScore, Long> {
    @Query("SELECT r FROM RiskScore r WHERE r.serviceId = :serviceId ORDER BY r.computedAt DESC")
    Optional<RiskScore> findLatestByServiceId(long serviceId);
}
