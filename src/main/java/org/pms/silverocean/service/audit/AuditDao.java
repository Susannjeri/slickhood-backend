package org.pms.silverocean.service.audit;

import org.pms.silverocean.database.audit.AuditLogRepo;
import org.pms.silverocean.database.audit.entities.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.pms.silverocean.service.audit.AuditSpecifications.searchAuditLog;

@Component
public class AuditDao {

    private final AuditLogRepo auditLogRepo;

    public AuditDao(AuditLogRepo auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    @Async
    public void saveAll(List<AuditLog> auditLogs) {
        auditLogRepo.saveAll(auditLogs);
    }

    public Page<AuditLog> getAuditLog(Optional<String> searchQuery, Pageable pageable) {
        return auditLogRepo.findAll(searchAuditLog(searchQuery), pageable);
    }

}
