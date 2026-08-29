package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ServiceRatingRepo;
import org.pms.silverocean.database.pms.entities.ServiceRating;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ServiceRatingDao {
    private final ServiceRatingRepo repo;
    private final AuditLogService auditLogService;

    public ServiceRatingDao(ServiceRatingRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ServiceRating rating, String auditAction) {
        rating.setLastModifiedDate(LocalDateTime.now());
        repo.save(rating);
        auditLogService.createAuditLog(rating, auditAction);
    }

    public Page<ServiceRating> findByServiceId(Pageable pageable, long serviceId) {
        return repo.findByServiceId(pageable, serviceId);
    }

    public boolean existsByBookingIdAndRater(long bookingId, long userId) {
        return repo.existsByBookingIdAndRater(bookingId, userId);
    }

    public int countHighlyRated(long serviceId, int minStars) {
        return repo.countHighlyRated(serviceId, minStars);
    }

    public double avgStars(long serviceId) {
        return repo.avgStars(serviceId);
    }

    public long countByServiceId(long serviceId) { return repo.countByServiceId(serviceId); }
}
