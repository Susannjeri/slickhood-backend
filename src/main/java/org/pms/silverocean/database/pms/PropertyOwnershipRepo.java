package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PropertyOwnershipRepo extends JpaRepository<PropertyOwnership, Long> {
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
}
