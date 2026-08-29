package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ServiceTierRepo;
import org.pms.silverocean.database.pms.entities.ServiceTier;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ServiceTierDao {
    private final ServiceTierRepo repo;
    private final AuditLogService auditLogService;

    public ServiceTierDao(ServiceTierRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ServiceTier tier, String auditAction) {
        tier.setLastModifiedDate(LocalDateTime.now());
        repo.save(tier);
        auditLogService.createAuditLog(tier, auditAction);
    }

    public Page<ServiceTier> findAllActive(Pageable pageable) {
        return repo.findAllActive(pageable);
    }

    public Optional<ServiceTier> findById(long id) {
        return repo.findById(id);
    }
}
