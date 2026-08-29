package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SaleTransaction;
import org.pms.silverocean.service.sales.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

public interface SaleTransactionRepo extends JpaRepository<SaleTransaction, Long> {
    List<SaleTransaction> findAllByBuyerUserIdAndActiveTrueOrderByCreatedOnDesc(long buyerUserId);
    List<SaleTransaction> findAllBySalesAgentUserIdAndActiveTrueOrderByCreatedOnDesc(long salesAgentUserId);
    @Query("SELECT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE p.createdBy=:ownerId AND s.active ORDER BY s.createdOn DESC")
    List<SaleTransaction> findAllByPropertyOwner(long ownerId);
    Optional<SaleTransaction> findByIdAndBuyerUserIdAndActiveTrue(long id, long buyerUserId);
    Optional<SaleTransaction> findByIdAndSalesAgentUserIdAndActiveTrue(long id, long salesAgentUserId);
    long countByBuyerUserIdAndStatusAndActiveTrue(long buyerUserId, SaleStatus status);
    long countBySalesAgentUserIdAndStatusAndActiveTrue(long salesAgentUserId, SaleStatus status);
    long countByBuyerUserIdAndActiveTrue(long buyerUserId);
    long countBySalesAgentUserIdAndActiveTrue(long salesAgentUserId);

    @Query("SELECT DISTINCT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE s.active AND s.createdOn >= :start AND s.createdOn < :end AND " +
            "(:privileged=true OR s.salesAgentUserId=:userId OR s.buyerUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY s.createdOn DESC")
    List<SaleTransaction> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end);
}
