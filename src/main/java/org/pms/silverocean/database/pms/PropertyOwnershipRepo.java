package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.pms.silverocean.service.estate.OwnershipView;
import java.util.List;
import java.util.Optional;

public interface PropertyOwnershipRepo extends JpaRepository<PropertyOwnership, Long> {
    Optional<PropertyOwnership> findBySourceSaleTransactionId(Long saleId);
    Optional<PropertyOwnership> findFirstByUnitIdAndActiveTrue(Long unitId);
    List<PropertyOwnership> findAllByHomeownerUserIdOrderByCreatedOnDesc(long userId);
    @Query("SELECT o FROM PropertyOwnership o JOIN Property p ON p.id=o.propertyId WHERE p.createdBy=:userId ORDER BY o.createdOn DESC")
    List<PropertyOwnership> findAllByPropertyOwner(long userId);
    @Query("SELECT o FROM PropertyOwnership o JOIN PropertyManager pm ON pm.propertyId=o.propertyId WHERE pm.userId=:userId AND pm.roleName=:roleName AND pm.active ORDER BY o.createdOn DESC")
    List<PropertyOwnership> findAllByManager(long userId, String roleName);
    long countByHomeownerUserIdAndActiveTrue(long userId);
    long countByPropertyIdAndActiveTrue(long propertyId);
    List<PropertyOwnership> findAllByPropertyIdAndActiveTrue(long propertyId);
    @Query("SELECT COUNT(o) FROM PropertyOwnership o JOIN PropertyManager pm ON pm.propertyId=o.propertyId WHERE o.active AND pm.active AND pm.userId=:userId AND pm.roleName=:roleName")
    long countActiveByManager(long userId, String roleName);

    String VIEW_SELECT = "SELECT new org.pms.silverocean.service.estate.OwnershipView(" +
            "o.id,o.propertyId,p.name,o.unitId,u.ref,o.homeownerUserId,h.fullName,h.email," +
            "o.ownershipStart,o.ownershipEnd,o.source,o.active,o.terminationReason) " +
            "FROM PropertyOwnership o JOIN Property p ON p.id=o.propertyId " +
            "LEFT JOIN Unit u ON u.id=o.unitId JOIN Users h ON h.id=o.homeownerUserId ";
    String VIEW_FILTER = " AND (:propertyId IS NULL OR o.propertyId=:propertyId) " +
            "AND (:active IS NULL OR o.active=:active) ORDER BY o.createdOn DESC";

    @Query(VIEW_SELECT + "WHERE o.homeownerUserId=:userId" + VIEW_FILTER)
    Page<OwnershipView> findPageByHomeowner(long userId, Long propertyId, Boolean active, Pageable pageable);

    @Query(VIEW_SELECT + "WHERE p.createdBy=:userId" + VIEW_FILTER)
    Page<OwnershipView> findPageByPropertyOwner(long userId, Long propertyId, Boolean active, Pageable pageable);

    @Query(VIEW_SELECT + "WHERE EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=o.propertyId " +
            "AND pm.userId=:userId AND pm.active)" + VIEW_FILTER)
    Page<OwnershipView> findPageByPropertyStaff(long userId, Long propertyId, Boolean active, Pageable pageable);

    @Query(VIEW_SELECT + "WHERE 1=1" + VIEW_FILTER)
    Page<OwnershipView> findAllOwnershipViews(Long propertyId, Boolean active, Pageable pageable);
}
