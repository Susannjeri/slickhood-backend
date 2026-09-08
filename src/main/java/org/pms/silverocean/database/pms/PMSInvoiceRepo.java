package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.service.payment.invoice.wrappers.AmountCurrencyProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.List;

public interface PMSInvoiceRepo extends JpaRepository<PMSInvoice, Long>, JpaSpecificationExecutor<PMSInvoice> {
    @Query("SELECT i FROM PMSInvoice i WHERE i.active=true AND i.paid=false AND i.pendingAmount>0 " +
            "AND i.billingType='RENTAL' AND i.dueDate<:today AND i.id>:afterId ORDER BY i.id")
    List<PMSInvoice> findRentalReminderCandidates(@Param("today") java.time.LocalDate today,
            @Param("afterId") long afterId, Pageable pageable);
    List<PMSInvoice> findAllByPropertyIdInAndActiveTrueAndPaidFalse(List<Long> propertyIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM PMSInvoice i WHERE i.id = :id")
    Optional<PMSInvoice> findByIdForUpdate(@Param("id") long id);

    Optional<PMSInvoice> findByRef(String ref);

    Page<PMSInvoice> findByBilledUserIdAndSubscriptionPlanCodeIsNotNullOrderByCreatedOnDesc(
            long billedUserId, Pageable pageable);

    Optional<PMSInvoice> findByRefAndPayToUserIdAndActiveTrue(String ref, long userId);

    @Query("SELECT p FROM PMSInvoice i JOIN PMSPayment p ON i.ref=p.billReference WHERE i.transactionInProgress AND p.channel=:channel AND p.status=:status")
    Set<PMSPayment> findByTransactionInProgressTrue(String channel, String status);

    @Query("SELECT i FROM PMSInvoice i WHERE i.id=:invoiceId AND i.active=true AND (i.payToUserId=:userId OR i.billedUserId=:userId)")
    Optional<PMSInvoice> findInvoiceForOwnerOrTenant(long invoiceId, long userId);

    @Query("SELECT i FROM PMSInvoice i WHERE i.ref=:ref AND i.active=true AND (i.payToUserId=:userId OR i.billedUserId=:userId)")
    Optional<PMSInvoice> findInvoiceForOwnerOrTenantByRef(String ref, long userId);

    @Query("SELECT i FROM PMSInvoice i WHERE i.id=:invoiceId AND i.active=true AND i.subscriptionPlanCode IS NOT NULL")
    Optional<PMSInvoice> findPlatformInvoice(long invoiceId);

    @Query("SELECT i FROM PMSInvoice i WHERE i.ref=:ref AND i.active=true AND i.subscriptionPlanCode IS NOT NULL")
    Optional<PMSInvoice> findPlatformInvoiceByRef(String ref);

    @Modifying
    @Query("UPDATE PMSInvoice i SET i.ref=:ref WHERE i.id=:id")
    void updateInvoiceRef(long id, String ref);

    @Query("SELECT SUM(i.amount) as amount, i.currency as currency FROM PMSInvoice i WHERE i.active AND i.paid AND i.createdOn >= :start AND i.createdOn < :end AND i.payToUserId = 0 " +
            "AND (i.billingType=:type OR (:type='SUBSCRIPTION' AND i.subscriptionPlanCode IS NOT NULL)) GROUP BY i.currency")
    Set<AmountCurrencyProjection> getSumOfPaidInvoicesUsingTypeAndDateRange(ZonedDateTime start, ZonedDateTime end, String type);

    @Query("SELECT COALESCE(COUNT(i), 0) FROM PMSInvoice i WHERE i.paid=:paid AND i.billedUserId=:userId")
    int countInvoicesByTenantWithUserIdAndPaidStatus(long userId, boolean paid);

    @Query("SELECT i FROM PMSInvoice i WHERE i.active AND i.createdOn >= :start AND i.createdOn < :end " +
            "AND ((:privileged=true AND i.subscriptionPlanCode IS NOT NULL) OR " +
            "(:privileged=false AND (i.billedUserId=:userId OR i.payToUserId=:userId))) ORDER BY i.createdOn DESC")
    List<PMSInvoice> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
}
