package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ProviderProfileRepo;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.sp.enums.ProviderProfileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Service
public class ProviderProfileDao {
    private final ProviderProfileRepo repo;
    private final AuditLogService auditLogService;

    public ProviderProfileDao(ProviderProfileRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ProviderProfile profile, String auditAction) {
        profile.setLastModifiedDate(LocalDateTime.now());
        repo.save(profile);
        auditLogService.createAuditLog(profile, auditAction);
    }

    public Optional<ProviderProfile> findByUserIdAndActive(long userId) {
        return repo.findByUserIdAndStatus(userId, ProviderProfileStatus.ACTIVE.name());
    }

    public Optional<ProviderProfile> findByUserId(long userId) {
        return repo.findByUserId(userId);
    }

    public Optional<ProviderProfile> findById(long id) {
        return repo.findById(id);
    }

    public List<ProviderProfile> findAllById(Iterable<Long> ids) { return repo.findAllById(ids); }

    public Page<ProviderProfile> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public boolean existsByUserId(long userId) {
        return repo.existsByUserId(userId);
    }
}
