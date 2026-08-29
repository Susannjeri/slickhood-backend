package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ServiceCategoryRepo;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ServiceCategoryDao {
    private final ServiceCategoryRepo repo;
    private final AuditLogService auditLogService;

    public ServiceCategoryDao(ServiceCategoryRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ServiceCategory category, String auditAction) {
        category.setLastModifiedDate(LocalDateTime.now());
        repo.save(category);
        auditLogService.createAuditLog(category, auditAction);
    }

    public Page<ServiceCategory> findAllActive(Pageable pageable) {
        return repo.findAllActive(pageable);
    }

    public Optional<ServiceCategory> findById(long id) {
        return repo.findById(id);
    }

    public Optional<ServiceCategory> findByNameAndActive(String name, boolean active) {
        return repo.findByNameAndActive(name, active);
    }
}
