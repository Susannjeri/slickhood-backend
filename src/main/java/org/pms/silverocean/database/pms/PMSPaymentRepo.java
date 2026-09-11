package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PMSPaymentRepo extends JpaRepository<PMSPayment, Long>, JpaSpecificationExecutor<PMSPayment> {
    Optional<PMSPayment> findByThirdPartyTransId(String thirdPartyTransId);
    Optional<PMSPayment> findFirstByBillReferenceAndChannelAndInProgressTrueOrderByCreatedOnDesc(
            String billReference, String channel);

    boolean existsByChannelAndProviderReceiptAndIdNot(String channel, String providerReceipt, Long id);

    boolean existsByThirdPartyTransIdAndBillReferenceAndCategoryAndStatus(
            String thirdPartyTransId, String billReference, String category, String status);

    @Query("SELECT p FROM PMSPayment p WHERE p.billReference IN" +
            " (SELECT pi.ref FROM PMSInvoice pi WHERE pi.active=true AND pi.ref=:invoiceRef AND (pi.billedUserId=:userId OR pi.payToUserId=:userId))" +
            " AND NOT (p.category =:category AND p.status =:status)")
    Page<PMSPayment> findByInvoiceRefAndUser(Pageable pageable, String invoiceRef, long userId, String category, String status);

    @Query("SELECT p FROM PMSPayment p WHERE  p.billReference IN" +
            " (SELECT pi.ref FROM PMSInvoice pi WHERE pi.active=true AND (pi.billedUserId=:userId OR pi.payToUserId=:userId))" +
            " AND NOT (p.category =:category AND p.status =:status)")
    Page<PMSPayment> findByUser(Pageable pageable, long userId, String category, String status);

    @Query("SELECT p FROM PMSPayment p WHERE p.billReference IN " +
            "(SELECT i.ref FROM PMSInvoice i WHERE i.active=true AND i.subscriptionPlanCode IS NOT NULL) " +
            "AND NOT (p.category=:category AND p.status=:status) AND " +
            "(:filter IS NULL OR LOWER(COALESCE(p.thirdPartyTransId,'')) LIKE LOWER(CONCAT('%',:filter,'%')) OR " +
            "LOWER(COALESCE(p.customerAccountNumber,'')) LIKE LOWER(CONCAT('%',:filter,'%')) OR " +
            "LOWER(COALESCE(p.billReference,'')) LIKE LOWER(CONCAT('%',:filter,'%')))")
    Page<PMSPayment> findPlatformPayments(Pageable pageable, String filter, String category, String status);

    @Query("SELECT p FROM PMSPayment p JOIN PMSInvoice i ON p.billReference=i.ref WHERE p.id=:paymentId AND i.billedUserId=:userId")
    Optional<PMSPayment> findByIdAndUserId(long paymentId, long userId);

    @Query("SELECT p FROM PMSPayment p JOIN PMSInvoice i ON p.billReference=i.ref WHERE p.id=:paymentId AND " +
            "i.active=true AND (i.billedUserId=:userId OR i.payToUserId=:userId)")
    Optional<PMSPayment> findByIdForAuthorizedUser(long paymentId, long userId);

    @Query("SELECT p FROM PMSPayment p JOIN PMSInvoice i ON p.billReference=i.ref WHERE p.id=:paymentId " +
            "AND i.active=true AND i.subscriptionPlanCode IS NOT NULL")
    Optional<PMSPayment> findPlatformPaymentById(long paymentId);


    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM PMSPayment p WHERE p.payToUserId=:userId AND p.createdOn >= :start AND p.createdOn < :end")
    Double sumMonthlyRevenueByLandlord(long userId, ZonedDateTime start, ZonedDateTime end);

    @Query("SELECT DISTINCT p FROM PMSPayment p JOIN PMSInvoice i ON p.billReference=i.ref " +
            "WHERE p.createdOn >= :start AND p.createdOn < :end AND " +
            "((:privileged=true AND i.subscriptionPlanCode IS NOT NULL) OR " +
            "(:privileged=false AND (i.billedUserId=:userId OR i.payToUserId=:userId))) ORDER BY p.createdOn DESC")
    List<PMSPayment> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
}
