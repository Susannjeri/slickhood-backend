package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ServiceBookingRepo extends JpaRepository<ServiceBooking, Long> {
    @Query("SELECT b FROM ServiceBooking b WHERE b.serviceId = :serviceId AND b.active = true")
    Page<ServiceBooking> findByServiceId(Pageable pageable, long serviceId);

    @Query("SELECT b FROM ServiceBooking b  JOIN ProviderService ps ON b.serviceId=ps.id WHERE (b.createdBy = :userId OR ps.createdBy=:userId) AND b.active = true")
    Page<ServiceBooking> findBycreatedByOrBookedServiceProvider(Pageable pageable, long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b WHERE b.id = :id AND b.active = true")
    Optional<ServiceBooking> findByIdForUpdate(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b WHERE b.invoiceRef = :invoiceRef AND b.active = true")
    Optional<ServiceBooking> findByInvoiceRefForUpdate(String invoiceRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b JOIN ProviderService ps ON b.serviceId=ps.id WHERE b.id=:id AND b.active=true " +
            "AND (b.createdBy=:userId OR ps.createdBy=:userId)")
    Optional<ServiceBooking> findByIdAndCustomerOrProviderForUpdate(long id, long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ServiceBooking b JOIN ProviderService ps ON b.serviceId=ps.id WHERE b.id = :id AND b.active = true AND ps.createdBy=:createdBy")
    Optional<ServiceBooking> findByIdAndServiceCreatedByForUpdate(long id, long createdBy);

    @Query("SELECT b FROM ServiceBooking b JOIN ProviderService ps ON b.serviceId=ps.id WHERE b.id = :id AND b.active = true AND b.createdBy=:createdBy")
    Optional<ServiceBooking> findByIdAndBookingCreatedByForRead(long id, long createdBy);

    @Query("SELECT COUNT(b) > 0 FROM ServiceBooking b WHERE b.id = :bookingId AND b.serviceId = :serviceId AND b.status = 'COMPLETED' AND b.active = true")
    boolean existsCompletedBookingForService(long bookingId, long serviceId);

    @Query("SELECT COALESCE(count(b), 0) FROM ServiceBooking b JOIN ProviderService ps ON b.serviceId=ps.id " +
            "WHERE ps.createdBy=:userId AND b.active AND b.createdOn >= :start AND b.createdOn < :end")
    int countBookingWithinSpecifiedMonth(long userId, ZonedDateTime start, ZonedDateTime end);


    @Query("SELECT COALESCE(AVG(r.stars), 0.0) FROM ServiceRating r JOIN ProviderService ps ON r.serviceId=ps.id  " +
            " WHERE ps.createdBy=:userId AND ps.active")
    double getRatingAveragePerServiceProvider(long userId);

    @Query("SELECT COALESCE(r.stars, 0) FROM ServiceRating r JOIN ProviderService ps ON r.serviceId=ps.id  " +
            " WHERE ps.createdBy=:userId ORDER BY r.createdOn DESC LIMIT 1")
    Integer getMostRecentRatingForServiceProvider(long userId);

    @Query("SELECT DISTINCT b FROM ServiceBooking b JOIN ProviderService ps ON b.serviceId=ps.id WHERE b.active AND b.createdOn >= :start AND b.createdOn < :end " +
            "AND (:privileged=true OR b.createdBy=:userId OR ps.createdBy=:userId) ORDER BY b.createdOn DESC")
    List<ServiceBooking> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end);
}
