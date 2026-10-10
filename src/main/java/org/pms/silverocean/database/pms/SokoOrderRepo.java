package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.ZonedDateTime;

public interface SokoOrderRepo extends JpaRepository<SokoOrder, Long> {
    Page<SokoOrder> findAllByActiveTrue(Pageable pageable);
    long countByActiveTrue();
    long countByStatusAndActiveTrue(String status);
    boolean existsByRiderIdAndStatusInAndActiveTrue(long riderId,List<String> statuses);
    List<SokoOrder> findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(long customerUserId);
    Page<SokoOrder> findAllByCustomerUserIdAndActiveTrue(long customerUserId,Pageable pageable);
    List<SokoOrder> findAllByStoreIdInAndActiveTrueOrderByCreatedOnDesc(List<Long> storeIds);
    Page<SokoOrder> findAllByStoreIdInAndActiveTrue(List<Long> storeIds,Pageable pageable);
    Page<SokoOrder> findAllByRiderIdInAndStatusNotInAndActiveTrue(List<Long> riderIds,List<String> excludedStatuses,Pageable pageable);
    Page<SokoOrder> findAllByRefundStatusInAndActiveTrue(List<String> refundStatuses,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SokoOrder> findByInvoiceRefAndActiveTrue(String invoiceRef);
    Optional<SokoOrder> findByCustomerUserIdAndCheckoutIdempotencyKeyAndActiveTrue(long customerUserId,String checkoutIdempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.customerUserId=:customerUserId and o.storeId=:storeId " +
            "and o.status='PENDING_PAYMENT' and o.active=true order by o.createdOn desc")
    List<SokoOrder> findPendingCheckoutForUpdate(@Param("customerUserId") long customerUserId,@Param("storeId") long storeId,Pageable pageable);
    @Query("SELECT o.destinationUnitId FROM SokoOrder o WHERE o.customerUserId=:customerUserId AND o.active=true " +
            "AND o.deliveryMethod='DELIVERY' AND o.status='COMPLETED' AND o.destinationUnitId IN :unitIds " +
            "ORDER BY o.completedAt DESC,o.id DESC")
    List<Long> findRecentCompletedDestinationUnitIds(long customerUserId,List<Long> unitIds,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.id=:id and o.active=true")
    Optional<SokoOrder> findByIdForUpdate(long id);
    Optional<SokoOrder> findByRiderAssignmentTokenHashAndActiveTrue(String riderAssignmentTokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.riderAssignmentTokenHash=:tokenHash and o.active=true")
    Optional<SokoOrder> findByRiderAssignmentTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.active=true and o.status in ('DELIVERY_ASSIGNED','ASSIGNMENT_ACCEPTED') " +
            "and o.riderAssignmentTokenRevokedAt is null and o.riderAssignmentTokenExpiresAt<:now order by o.riderAssignmentTokenExpiresAt")
    List<SokoOrder> findExpiredRiderAssignmentsForUpdate(@Param("now") ZonedDateTime now,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.active=true and o.storeId in :storeIds " +
            "and o.status in ('DELIVERY_ASSIGNED','ASSIGNMENT_ACCEPTED') and o.riderAssignmentTokenRevokedAt is null " +
            "and o.riderAssignmentTokenExpiresAt<:now order by o.riderAssignmentTokenExpiresAt")
    List<SokoOrder> findExpiredRiderAssignmentsForStoresForUpdate(@Param("storeIds") List<Long> storeIds,
                                                                  @Param("now") ZonedDateTime now,Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.active=true and o.status='PENDING_PAYMENT' and o.stockReleased=false and o.reservationExpiresAt<:now")
    List<SokoOrder> findExpiredReservations(ZonedDateTime now,Pageable pageable);

    @Query("SELECT o FROM SokoOrder o JOIN SokoStore s ON s.id=o.storeId WHERE o.active AND o.createdOn >= :start AND o.createdOn < :end " +
            "AND (:privileged=true OR o.customerUserId=:userId OR s.ownerUserId=:userId) ORDER BY o.createdOn DESC")
    List<SokoOrder> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
}
