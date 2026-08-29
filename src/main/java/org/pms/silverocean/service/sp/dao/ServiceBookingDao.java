package org.pms.silverocean.service.sp.dao;

import org.pms.silverocean.database.pms.ServiceBookingRepo;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.service.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ServiceBookingDao {
    private final ServiceBookingRepo repo;
    private final AuditLogService auditLogService;

    public ServiceBookingDao(ServiceBookingRepo repo, AuditLogService auditLogService) {
        this.repo = repo;
        this.auditLogService = auditLogService;
    }

    public void save(ServiceBooking booking, String auditAction) {
        booking.setLastModifiedDate(LocalDateTime.now());
        repo.save(booking);
        auditLogService.createAuditLog(booking, auditAction);
    }

    public Page<ServiceBooking> findByServiceId(Pageable pageable, long serviceId) {
        return repo.findByServiceId(pageable, serviceId);
    }

    public Page<ServiceBooking> findByCreatedByOrBookedServiceProvider(Pageable pageable, long userId) {
        return repo.findBycreatedByOrBookedServiceProvider(pageable, userId);
    }

    public Optional<ServiceBooking> findByIdForUpdate(long id) {
        return repo.findByIdForUpdate(id);
    }

    public Optional<ServiceBooking> findByInvoiceRefForUpdate(String invoiceRef) {
        return repo.findByInvoiceRefForUpdate(invoiceRef);
    }

    public Optional<ServiceBooking> findByIdAndCustomerOrProviderForUpdate(long id, long userId) {
        return repo.findByIdAndCustomerOrProviderForUpdate(id, userId);
    }

    public Optional<ServiceBooking> findByIdAndServiceCreatedByForUpdate(long id, long userId) {
        return repo.findByIdAndServiceCreatedByForUpdate(id, userId);
    }

    public Optional<ServiceBooking> findByIdAndBookingCreatedByForRead(long id, long userId) {
        return repo.findByIdAndBookingCreatedByForRead(id, userId);
    }

    public Optional<ServiceBooking> findById(long id) {
        return repo.findById(id);
    }

    public boolean existsCompletedBookingForService(long bookingId, long serviceId) {
        return repo.existsCompletedBookingForService(bookingId, serviceId);
    }
}
