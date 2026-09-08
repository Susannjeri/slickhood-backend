package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SaleTransaction;
import org.pms.silverocean.service.sales.SaleStatus;
import org.pms.silverocean.service.sales.SaleView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

public interface SaleTransactionRepo extends JpaRepository<SaleTransaction, Long> {
    @Query(VIEW_SELECT + "WHERE s.active AND p.active AND p.managementMode=org.pms.silverocean.service.property.PMSPropertyManagementMode.SALE AND " +
            "((:owner=true AND p.createdBy=:userId) OR (:owner=false AND EXISTS (SELECT 1 FROM PropertyManager pm " +
            "WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active AND pm.roleName=:roleName AND pm.inviteId=:assignmentId))) " +
            SEARCH_FILTER + " ORDER BY s.createdOn DESC,s.id DESC")
    Page<SaleView> findViewPageBySalesScope(long userId, boolean owner, String roleName, Long assignmentId, String search, Pageable pageable);
    default Page<SaleView> findViewPageBySalesScope(long userId, boolean owner, String roleName, Long assignmentId, Pageable pageable) {
        return findViewPageBySalesScope(userId, owner, roleName, assignmentId, null, pageable);
    }
    String VIEW_SELECT = "SELECT new org.pms.silverocean.service.sales.SaleView(" +
            "s.id,s.propertyId,p.name,s.unitId,u.ref,s.salesAgentUserId,a.fullName,s.buyerUserId,b.fullName," +
            "COALESCE(b.email,s.invitedBuyerEmail),s.status,s.askingPrice,s.offerAmount,s.escrowRequiredAmount,s.escrowInvoiceId,s.currency," +
            "s.offerAcceptedAt,s.completedAt,s.notes,s.createdOn) FROM SaleTransaction s " +
            "JOIN Property p ON p.id=s.propertyId JOIN Unit u ON u.id=s.unitId " +
            "JOIN Users a ON a.id=s.salesAgentUserId LEFT JOIN Users b ON b.id=s.buyerUserId ";
    String SEARCH_FILTER = "AND (:search IS NULL OR LOWER(COALESCE(b.fullName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(COALESCE(b.email,s.invitedBuyerEmail)) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(p.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(u.ref) LIKE LOWER(CONCAT('%',:search,'%')))";
    List<SaleTransaction> findAllByBuyerUserIdAndActiveTrueOrderByCreatedOnDesc(long buyerUserId);
    List<SaleTransaction> findAllBySalesAgentUserIdAndActiveTrueOrderByCreatedOnDesc(long salesAgentUserId);
    @Query("SELECT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE p.createdBy=:ownerId AND s.active ORDER BY s.createdOn DESC")
    List<SaleTransaction> findAllByPropertyOwner(long ownerId);
    Optional<SaleTransaction> findByIdAndBuyerUserIdAndActiveTrue(long id, long buyerUserId);
    Optional<SaleTransaction> findByIdAndSalesAgentUserIdAndActiveTrue(long id, long salesAgentUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaleTransaction s WHERE s.id=:id AND s.active")
    Optional<SaleTransaction> findByIdForUpdate(long id);
    boolean existsByUnitIdAndActiveTrueAndStatusNot(long unitId, SaleStatus status);
    @Query("SELECT s FROM SaleTransaction s WHERE s.active AND s.buyerUserId=:userId ORDER BY s.createdOn DESC")
    Page<SaleTransaction> findPageByBuyer(long userId, Pageable pageable);
    @Query("SELECT DISTINCT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE s.active AND " +
            "(p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY s.createdOn DESC")
    Page<SaleTransaction> findPageByPropertyAccess(long userId, Pageable pageable);
    @Query("SELECT s FROM SaleTransaction s WHERE s.active ORDER BY s.createdOn DESC")
    Page<SaleTransaction> findAllActive(Pageable pageable);
    @Query(value = VIEW_SELECT + "WHERE s.active AND s.buyerUserId=:userId " + SEARCH_FILTER + " ORDER BY s.createdOn DESC",
            countQuery = "SELECT COUNT(s) FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId JOIN Unit u ON u.id=s.unitId LEFT JOIN Users b ON b.id=s.buyerUserId WHERE s.active AND s.buyerUserId=:userId " + SEARCH_FILTER)
    Page<SaleView> findViewPageByBuyer(long userId, String search, Pageable pageable);
    default Page<SaleView> findViewPageByBuyer(long userId, Pageable pageable) {
        return findViewPageByBuyer(userId, null, pageable);
    }
    @Query(value = "SELECT DISTINCT new org.pms.silverocean.service.sales.SaleView(" +
            "s.id,s.propertyId,p.name,s.unitId,u.ref,s.salesAgentUserId,a.fullName,s.buyerUserId,b.fullName," +
            "COALESCE(b.email,s.invitedBuyerEmail),s.status,s.askingPrice,s.offerAmount,s.escrowRequiredAmount,s.escrowInvoiceId,s.currency," +
            "s.offerAcceptedAt,s.completedAt,s.notes,s.createdOn) FROM SaleTransaction s " +
            "JOIN Property p ON p.id=s.propertyId JOIN Unit u ON u.id=s.unitId JOIN Users a ON a.id=s.salesAgentUserId " +
            "LEFT JOIN Users b ON b.id=s.buyerUserId WHERE s.active AND (p.createdBy=:userId OR EXISTS " +
            "(SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY s.createdOn DESC",
            countQuery = "SELECT COUNT(DISTINCT s) FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE s.active AND " +
                    "(p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active))")
    Page<SaleView> findViewPageByPropertyAccess(long userId, Pageable pageable);
    @Query(value = VIEW_SELECT + "WHERE s.active " + SEARCH_FILTER + " ORDER BY s.createdOn DESC",
            countQuery = "SELECT COUNT(s) FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId JOIN Unit u ON u.id=s.unitId LEFT JOIN Users b ON b.id=s.buyerUserId WHERE s.active " + SEARCH_FILTER)
    Page<SaleView> findAllActiveViews(String search, Pageable pageable);
    default Page<SaleView> findAllActiveViews(Pageable pageable) {
        return findAllActiveViews(null, pageable);
    }
    @Query("SELECT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE s.id=:id AND s.active AND " +
            "(p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active))")
    Optional<SaleTransaction> findByIdAndPropertyAccess(long id, long userId);
    long countByBuyerUserIdAndStatusAndActiveTrue(long buyerUserId, SaleStatus status);
    long countBySalesAgentUserIdAndStatusAndActiveTrue(long salesAgentUserId, SaleStatus status);
    long countByBuyerUserIdAndActiveTrue(long buyerUserId);
    long countBySalesAgentUserIdAndActiveTrue(long salesAgentUserId);

    @Query("SELECT DISTINCT s FROM SaleTransaction s JOIN Property p ON p.id=s.propertyId WHERE s.active AND s.createdOn >= :start AND s.createdOn < :end AND " +
            "(:privileged=true OR s.salesAgentUserId=:userId OR s.buyerUserId=:userId OR p.createdBy=:userId " +
            "OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=s.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY s.createdOn DESC")
    List<SaleTransaction> findForReport(long userId, boolean privileged, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
}
