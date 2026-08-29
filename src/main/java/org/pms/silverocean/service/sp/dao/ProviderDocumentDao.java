package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ProviderDocumentRepo;
import org.pms.silverocean.database.pms.entities.ProviderDocument;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class ProviderDocumentDao {
    private final ProviderDocumentRepo repo;
    private final AuditLogService auditLogService;

    public ProviderDocumentDao(ProviderDocumentRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ProviderDocument document, String auditAction) {
        document.setLastModifiedDate(LocalDateTime.now());
        repo.save(document);
        auditLogService.createAuditLog(document, auditAction);
    }

    public Page<ProviderDocument> findByServiceId(long serviceId, Pageable pageable) {
        return repo.findByServiceId(serviceId, pageable);
    }

    public Optional<ProviderDocument> findByIdAndServiceId(long id, long serviceId) {
        return repo.findByIdAndServiceId(id, serviceId);
    }

    public Optional<ProviderDocument> findById(long id) {
        return repo.findById(id);
    }

    public Set<String> findUploadedDocumentTypesByServiceId(long serviceId) {
        return repo.findUploadedDocumentTypesByServiceId(serviceId);
    }

    public Set<String> findVerifiedDocumentTypesByServiceId(long serviceId) {
        return repo.findVerifiedDocumentTypesByServiceId(serviceId);
    }
}
