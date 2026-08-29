package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ComplaintCaseRepo;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ComplaintCaseDao {
    private final ComplaintCaseRepo repo;
    private final AuditLogService auditLogService;

    public ComplaintCaseDao(ComplaintCaseRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ComplaintCase complaintCase, String auditAction) {
        complaintCase.setLastModifiedDate(LocalDateTime.now());
        repo.save(complaintCase);
        auditLogService.createAuditLog(complaintCase, auditAction);
    }

    public Page<ComplaintCase> findByServiceId(Pageable pageable, long serviceId) {
        return repo.findByServiceId(pageable, serviceId);
    }

    public Page<ComplaintCase> findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(Pageable pageable, long serviceId, long userId) {
        return repo.findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(pageable, serviceId, userId, PMSRole.SUPER_ADMIN.getName());
    }

    public Page<ComplaintCase> findOpenAndUnderReview(Pageable pageable) {
        return repo.findOpenAndUnderReview(pageable);
    }

    public Optional<ComplaintCase> findByIdForUpdate(long id) {
        return repo.findByIdForUpdate(id);
    }

    public Optional<ComplaintCase> findById(long id) {
        return repo.findById(id);
    }

    public int countUpheldComplaintsForProfile(long profileId) {
        return repo.countUpheldComplaintsForProfile(profileId);
    }

    public boolean existsOpenComplaintForBooking(long bookingId) {
        return repo.existsOpenComplaintForBooking(bookingId);
    }
}
