package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PropertyRepo extends JpaRepository<Property, Long>,  JpaSpecificationExecutor<Property> {
    Optional<Property> findByNameAndAddressAndCreatedBy(String name, String address, long createdBy);
    Optional<Property> findByIdAndCreatedByAndActiveTrue(long id, long createdBy);

    @Query(nativeQuery = true,value = "SELECT * FROM pms_property p WHERE p.has_units IS FALSE AND p.active AND DATEDIFF(NOW(), p.created_on) > :days")
    Page<Property> findAllByHasUnitsFalseAndActiveTrueAndDaysElasped(int days, Pageable pageable);
    @Query("SELECT COUNT(u) FROM Property p JOIN Unit u ON p.id=u.propertyId WHERE p.id=:propertyId AND p.active AND p.hasUnits AND u.active AND u.occupied")
    Integer countOccupiedUnitsWithinProperty(long propertyId);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND" +
            " (p.createdBy=:userId " +
            " OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active)" +
            " OR EXISTS (SELECT 1 FROM UnitTenant ut JOIN Unit u ON u.id=ut.unitId WHERE u.propertyId=p.id AND ut.userId=:userId AND ut.active))")
    Optional<Property> findByIdAndStaffOrOwnerOrTenant(long id, long userId);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND EXISTS (SELECT 1 FROM Unit u JOIN UnitTenant ut ON ut.unitId=u.id WHERE u.propertyId=p.id AND ut.userId=:userId AND ut.active)")
    Optional<Property> findByIdAndTenant(long id, long userId);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.roleName=:roleName AND pm.active)")
    Optional<Property> findByIdAndManagerRole(long id, long userId, String roleName);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND EXISTS (SELECT 1 FROM PropertyOwnership o WHERE o.propertyId=p.id AND o.homeownerUserId=:userId AND o.active)")
    Optional<Property> findByIdAndHomeowner(long id, long userId);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND EXISTS (SELECT 1 FROM SaleTransaction s WHERE s.propertyId=p.id AND s.buyerUserId=:userId AND s.active)")
    Optional<Property> findByIdAndBuyer(long id, long userId);

    @Query("SELECT p FROM Property p WHERE p.active AND p.id=:id AND" +
            " (p.createdBy=:userId" +
            " OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active))")
    Optional<Property> findByIdAndStaffOrOwner(long id, long userId);


    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(u.id, u.fullName) FROM Users u INNER JOIN Property p ON u.id = p.createdBy")
    Page<IdNameDescDTO> findAllLandlordNames(Pageable pageable);

    @Query("SELECT DISTINCT new org.pms.silverocean.service.wrappers.IdNameDescDTO(u.id, u.fullName) " +
            "FROM Users u " +
            "INNER JOIN Property p ON u.id = p.createdBy " +
            "WHERE u.fullName LIKE %:name% AND u.active = true")
    Page<IdNameDescDTO> findAllLandlordNamesByName(Pageable pageable, String name);


    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(u.id, u.fullName) FROM Users u INNER JOIN UnitTenant ut ON u.id = ut.userId WHERE ut.leaseAccepted")
    Page<IdNameDescDTO> findAllTenantNames(Pageable pageable);

    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(u.id, u.fullName) FROM Users u INNER JOIN UnitTenant ut ON u.id = ut.userId WHERE u.fullName like %:name% AND ut.leaseAccepted")
    Page<IdNameDescDTO> findAllTenantNamesByName(Pageable pageable, String name);

    @Modifying
    @Query("UPDATE Property p SET p.imagePath=:imagePath WHERE p.id=:propertyId")
    void updateImagePath(long propertyId, String imagePath);

    @Query("SELECT coalesce(count(p), 0) FROM Property p WHERE p.active AND p.createdBy=:userId")
    int countPropertyByLandlord(long userId);

    @Query("SELECT coalesce(count(p), 0) FROM Property p WHERE p.active")
    int countAllActiveProperty();

    @Query("SELECT coalesce(count(p), 0) FROM Property p JOIN PropertyManager pm ON p.id=pm.propertyId" +
            " WHERE p.active AND pm.userId=:userId AND pm.roleName='PROPERTY_MANAGER'")
    int countPropertyByPropertyManager(long userId);
}
