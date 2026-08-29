package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.RefereeRepo;
import org.pms.silverocean.database.pms.entities.Referee;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RefereeDao {
    private final RefereeRepo repo;
    private final AuditLogService auditLogService;

    public RefereeDao(RefereeRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(Referee referee, String auditAction) {
        referee.setLastModifiedDate(LocalDateTime.now());
        repo.save(referee);
        auditLogService.createAuditLog(referee, auditAction);
    }

    public Page<Referee> findByProfileId(long profileId, Pageable pageable) {
        return repo.findByProfileId(profileId, pageable);
    }

    public Optional<Referee> findByIdAndProfileId(long id, long profileId) {
        return repo.findByIdAndProfileId(id, profileId);
    }

    public Optional<Referee> findById(long id) {
        return repo.findById(id);
    }

    public int countByProfileId(long profileId) {
        return repo.countByProfileId(profileId);
    }

    public int countVerifiedByProfileId(long profileId) {
        return repo.countVerifiedByProfileId(profileId);
    }
}
