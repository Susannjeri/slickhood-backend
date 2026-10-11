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

    @Query("SELECT b FROM ServiceBooking b WHERE b.createdBy=:createdBy AND b.clientRequestId=:clientRequestId AND b.active=true")
    Optional<ServiceBooking> findByCreatedByAndClientRequestId(long createdBy, String clientRequestId);

    @Query("SELECT b.id FROM ServiceBooking b WHERE b.active = true " +
            "AND b.status = 'AWAITING_CUSTOMER_CONFIRMATION' " +
            "AND b.disputeDeadlineAt IS NOT NULL AND b.disputeDeadlineAt <= :now " +
            "AND NOT EXISTS (SELECT c.id FROM ComplaintCase c WHERE c.bookingId = b.id AND c.active = true " +
            "AND c.status IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED')) " +
            "ORDER BY b.disputeDeadlineAt ASC, b.id ASC")
    List<Long> findIdsReadyForAutoFinalization(ZonedDateTime now, Pageable pageable);

    @Query("SELECT b.id FROM ServiceBooking b WHERE b.active = true " +
            "AND b.status IN ('PENDING', 'AWAITING_PAYMENT') " +
            "AND b.scheduledAt IS NOT NULL AND b.scheduledAt <= :now " +
            "ORDER BY b.scheduledAt ASC, b.id ASC")
    List<Long> findIdsReadyForRequestExpiry(ZonedDateTime now, Pageable pageable);

    @Query("SELECT COUNT(b) > 0 FROM ServiceBooking b WHERE b.active = true " +
            "AND b.paymentAccountId = :accountId AND (" +
            "(b.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID', 'PAID') " +
            "AND NOT (b.status = 'CANCELLED' AND b.paymentStatus = 'UNPAID') " +
            "AND NOT (b.status = 'COMPLETED' AND b.settlementStatus = 'CONFIRMED')) " +
            "OR b.refundStatus IN ('REQUESTED', 'PROCESSING', 'FAILED') " +
            "OR b.settlementStatus IN ('REQUESTED', 'PROCESSING', 'FAILED'))")
    boolean existsMarketplaceDependencyOnPaymentAccount(long accountId);

    @Query("SELECT COUNT(b) > 0 FROM ServiceBooking b WHERE b.id = :bookingId AND b.serviceId = :serviceId AND b.status = 'COMPLETED' AND b.active = true")
    boolean existsCompletedBookingForService(long bookingId, long serviceId);

    @Query("SELECT COUNT(b) > 0 FROM ServiceBooking b WHERE b.serviceId = :serviceId AND b.active = true " +
            "AND (b.status IS NULL OR b.status NOT IN ('COMPLETED', 'CANCELLED', 'NO_SHOW'))")
    boolean existsNonTerminalBookingForService(long serviceId);

    @Query("SELECT COUNT(i) > 0 FROM ServiceBooking b JOIN PMSInvoice i ON i.ref = b.invoiceRef " +
            "WHERE b.serviceId = :serviceId AND i.active = true AND i.paid = false")
    boolean existsActiveUnpaidInvoiceForService(long serviceId);

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
    List<ServiceBooking> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);

    @Query(value = "SELECT b FROM ServiceBooking b WHERE b.active=true AND " +
            "(((:financeType IS NULL OR :financeType='REFUND') AND b.refundStatus IN :statuses) OR " +
            "((:financeType IS NULL OR :financeType='SETTLEMENT') AND b.settlementStatus IN :statuses)) " +
            "ORDER BY b.createdOn ASC, b.id ASC",
            countQuery = "SELECT COUNT(b) FROM ServiceBooking b WHERE b.active=true AND " +
                    "(((:financeType IS NULL OR :financeType='REFUND') AND b.refundStatus IN :statuses) OR " +
                    "((:financeType IS NULL OR :financeType='SETTLEMENT') AND b.settlementStatus IN :statuses))")
    Page<ServiceBooking> findFinanceQueue(Pageable pageable, String financeType, List<String> statuses);
}
