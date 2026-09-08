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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PropertyOwnership o WHERE o.id=:id AND o.active")
    Optional<PropertyOwnership> findActiveForUpdate(long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PropertyOwnership o WHERE o.unitId=:unitId AND o.active")
    Optional<PropertyOwnership> findCurrentForUpdate(long unitId);
    boolean existsByPropertyIdAndHomeownerUserIdAndActiveTrue(long propertyId, long homeownerUserId);
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
            "AND (:active IS NULL OR o.active=:active) " +
            "AND (:search IS NULL OR LOWER(h.fullName) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(h.email) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(p.name) LIKE LOWER(CONCAT('%',:search,'%')) " +
            "OR LOWER(COALESCE(u.ref,'')) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "ORDER BY o.createdOn DESC,o.id DESC";

    @Query(VIEW_SELECT + "WHERE p.active AND p.managementMode=org.pms.silverocean.service.property.PMSPropertyManagementMode.SERVICE_CHARGE " +
            "AND ((:owner=true AND p.createdBy=:userId) OR (:owner=false AND EXISTS (SELECT 1 FROM PropertyManager pm " +
            "WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.roleName=:roleName AND pm.inviteId=:assignmentId AND pm.active)))" + VIEW_FILTER)
    Page<OwnershipView> findPageByEstateScope(long userId, boolean owner, String roleName, Long assignmentId,
                                             Long propertyId, Boolean active, String search, Pageable pageable);
    default Page<OwnershipView> findPageByEstateScope(long userId, boolean owner, String roleName, Long assignmentId,
                                                      Long propertyId, Boolean active, Pageable pageable) {
        return findPageByEstateScope(userId, owner, roleName, assignmentId, propertyId, active, null, pageable);
    }

    @Query(VIEW_SELECT + "WHERE o.homeownerUserId=:userId" + VIEW_FILTER)
    Page<OwnershipView> findPageByHomeowner(long userId, Long propertyId, Boolean active, String search, Pageable pageable);
    default Page<OwnershipView> findPageByHomeowner(long userId, Long propertyId, Boolean active, Pageable pageable) {
        return findPageByHomeowner(userId, propertyId, active, null, pageable);
    }

    @Query(VIEW_SELECT + "WHERE p.createdBy=:userId" + VIEW_FILTER)
    Page<OwnershipView> findPageByPropertyOwner(long userId, Long propertyId, Boolean active, String search, Pageable pageable);
    default Page<OwnershipView> findPageByPropertyOwner(long userId, Long propertyId, Boolean active, Pageable pageable) {
        return findPageByPropertyOwner(userId, propertyId, active, null, pageable);
    }

    @Query(VIEW_SELECT + "WHERE EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=o.propertyId " +
            "AND pm.userId=:userId AND pm.active)" + VIEW_FILTER)
    Page<OwnershipView> findPageByPropertyStaff(long userId, Long propertyId, Boolean active, String search, Pageable pageable);
    default Page<OwnershipView> findPageByPropertyStaff(long userId, Long propertyId, Boolean active, Pageable pageable) {
        return findPageByPropertyStaff(userId, propertyId, active, null, pageable);
    }

    @Query(VIEW_SELECT + "WHERE 1=1" + VIEW_FILTER)
    Page<OwnershipView> findAllOwnershipViews(Long propertyId, Boolean active, String search, Pageable pageable);
    default Page<OwnershipView> findAllOwnershipViews(Long propertyId, Boolean active, Pageable pageable) {
        return findAllOwnershipViews(propertyId, active, null, pageable);
    }
}
