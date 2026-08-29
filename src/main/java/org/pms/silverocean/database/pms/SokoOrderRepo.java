package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.SokoOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

public interface SokoOrderRepo extends JpaRepository<SokoOrder, Long> {
    List<SokoOrder> findAllByCustomerUserIdAndActiveTrueOrderByCreatedOnDesc(long customerUserId);
    List<SokoOrder> findAllByStoreIdInAndActiveTrueOrderByCreatedOnDesc(List<Long> storeIds);
    Optional<SokoOrder> findByInvoiceRefAndActiveTrue(String invoiceRef);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SokoOrder o where o.id=:id and o.active=true")
    Optional<SokoOrder> findByIdForUpdate(long id);
    @Query("select o from SokoOrder o where o.active=true and o.status='PENDING_PAYMENT' and o.stockReleased=false and o.reservationExpiresAt<:now")
    List<SokoOrder> findExpiredReservations(ZonedDateTime now);

    @Query("SELECT o FROM SokoOrder o JOIN SokoStore s ON s.id=o.storeId WHERE o.active AND o.createdOn >= :start AND o.createdOn < :end " +
            "AND (:privileged=true OR o.customerUserId=:userId OR s.ownerUserId=:userId) ORDER BY o.createdOn DESC")
    List<SokoOrder> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end);
}
