package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.RiskScoreRepo;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RiskScoreDao {
    private final RiskScoreRepo repo;
    private final AuditLogService auditLogService;

    public RiskScoreDao(RiskScoreRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(RiskScore riskScore, String auditAction) {
        riskScore.setLastModifiedDate(LocalDateTime.now());
        repo.save(riskScore);
        auditLogService.createAuditLog(riskScore, auditAction);
    }

    public Optional<RiskScore> findLatestByServiceId(long serviceId) {
        return repo.findLatestByServiceId(serviceId);
    }
}
