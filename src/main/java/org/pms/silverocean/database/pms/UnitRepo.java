package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.property.wrappers.PropertyNameAddressAndTypeProjection;
import org.pms.silverocean.service.property.wrappers.TenantNameEmailPhoneAndUnitRefProjection;
import org.pms.silverocean.service.property.wrappers.UnitTenantProjection;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.visitor.projections.GuardHostOptionProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UnitRepo extends JpaRepository<Unit, Long>, JpaSpecificationExecutor<Unit> {
    @Query("SELECT p.createdBy FROM Unit u JOIN Property p ON p.id=u.propertyId WHERE u.id=:unitId AND u.active AND p.active")
    Optional<Long> findPropertyOwnerId(long unitId);
    @Query("SELECT DISTINCT u FROM Unit u JOIN Property p ON p.id=u.propertyId WHERE u.active AND p.active AND " +
            "(:privileged=true OR p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active) " +
            "OR EXISTS (SELECT 1 FROM UnitTenant ut WHERE ut.unitId=u.id AND ut.userId=:userId AND ut.active) " +
            "OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active)) " +
            "ORDER BY p.name, u.ref")
    List<Unit> findForReport(long userId, boolean privileged, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Unit u WHERE u.id = :id")
    Optional<Unit> findAndLockById(@Param("id") long id);

    Optional<Unit> findByRefAndPropertyId(String ref, long propertyId);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.id=:id AND u.createdBy=:createdBy AND u.active")
    Optional<DbUnitDTO> findDTOByIdAndCreatedByAndActiveTrue(Long id, long createdBy);

    Optional<Unit> findByIdAndCreatedByAndActiveTrue(Long id, long createdBy);

    @Query("SELECT u FROM Unit u JOIN Invite li ON u.id=li.entityId WHERE li.type=:inviteType AND li.token=:token AND li.active AND li.expiryDate >= CURRENT_TIMESTAMP AND u.active")
    Optional<Unit> findByTokenAndActiveTrue(String token, String inviteType);

    @Query("SELECT  new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id JOIN Invite li ON u.id=li.entityId WHERE li.type=:inviteType AND li.token=:token AND li.active AND li.expiryDate >= CURRENT_TIMESTAMP AND u.active")
    Optional<DbUnitDTO> findUnitDtoByTokenAndActiveTrue(String token, String inviteType);

    @Modifying
    @Query("UPDATE Unit u set u.active=false where u.propertyId=:propertyId")
    void deactivateAllUnitsWithinProperty(long propertyId);

    @Query("SELECT p.name as name, p.address as address, p.type as type FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.id=:unitId")
    Optional<PropertyNameAddressAndTypeProjection> getPropertyDetailsFromUnitId(long unitId);

    @Query("SELECT user.fullName as tenantName, user.email as tenantEmail, user.phoneNumber as tenantPhone, u.ref as unitRef FROM Unit u JOIN UnitTenant ut ON u.id=ut.unitId JOIN Users user ON ut.userId=user.id WHERE u.id=:unitId AND user.id=:tenantUserId")
    Optional<TenantNameEmailPhoneAndUnitRefProjection> getTenantAndUnitDetailsByUnitId(long unitId, long tenantUserId);

    @Query("SELECT u FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND (p.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active))")
    Optional<Unit>  findByIdAndStaffOrOwner(Long id, long userId);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND " +
            "(p.createdBy=:userId " +
            " OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active)" +
            " OR EXISTS (SELECT 1 FROM UnitTenant ut WHERE ut.unitId=u.id AND ut.userId=:userId AND ut.active)" +
            " OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active))")
    Optional<DbUnitDTO> findByIdAndStaffOrOwnerOrTenant(Long id, long userId);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND EXISTS (SELECT 1 FROM UnitTenant ut WHERE ut.unitId=u.id AND ut.userId=:userId AND ut.active)")
    Optional<DbUnitDTO> findDTOByIdAndTenant(Long id, long userId);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.roleName=:roleName AND pm.active)")
    Optional<DbUnitDTO> findDTOByIdAndManagerRole(Long id, long userId, String roleName);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND EXISTS (SELECT 1 FROM PropertyOwnership o WHERE o.unitId=u.id AND o.homeownerUserId=:userId AND o.active)")
    Optional<DbUnitDTO> findDTOByIdAndHomeowner(Long id, long userId);

    @Query("SELECT new org.pms.silverocean.service.property.wrappers.DbUnitDTO(u, p.type) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND u.id=:id AND EXISTS (SELECT 1 FROM SaleTransaction s WHERE s.unitId=u.id AND s.buyerUserId=:userId AND s.active)")
    Optional<DbUnitDTO> findDTOByIdAndBuyer(Long id, long userId);

    Integer countAllByPropertyIdAndActiveTrue(Long propertyId);
    List<Unit> findAllByPropertyIdInAndActiveTrue(List<Long> propertyIds);

    @Query("SELECT ut.id as id, us.id as userId, us.fullName as name, us.email as email, us.phoneNumber as phoneNumber, ut.createdOn as createdOn, " +
            "l.id as leaseId, ut.leaseAccepted as leaseAccepted, l.tenantSignedDate as tenantSignedDate, " +
            "(SELECT owner.fullName FROM Users owner where owner.id=l.signedByManagerId) as signedByManagerName, l.managerSignedDate as managerSignedDate FROM Users us JOIN UnitTenant ut ON us.id=ut.userId " +
            "JOIN Unit u ON ut.unitId=u.id JOIN Property p ON u.propertyId=p.id JOIN Lease l ON l.tenantId=ut.id WHERE u.id=:unitId AND (p.createdBy=:userId OR  " +
            "EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=p.id AND pm.userId=:userId AND pm.active)) AND u.active AND ut.active")
    Page<UnitTenantProjection> findUnitTenantsByUnitIDAndUnitStaffAndActive(Pageable pageable, long unitId, long userId);

    @Query("SELECT u FROM Users u JOIN PropertyManager pm ON u.id=pm.userId JOIN Unit un ON pm.propertyId=un.propertyId WHERE un.id=:unitId AND un.active AND pm.active AND pm.roleName=:roleName")
    List<Users> findPropertyManagerByUnitID(long unitId, String roleName);

    @Query("SELECT owner FROM Unit un JOIN Property p ON p.id=un.propertyId JOIN Users owner ON owner.id=p.createdBy WHERE un.id=:unitId AND un.active AND p.active")
    Users getLandlordDetails(long unitId);

    @Query("SELECT u.id as unitId, u.propertyId as propertyId, u.ref as unitRef, p.name as propertyName  FROM UnitTenant ut JOIN Unit u ON ut.unitId = u.id JOIN Property p ON u.propertyId=p.id WHERE ut.unitId=:unitId AND ut.userId=:userId AND ut.active")
    Optional<PropertyIdUnitRefPropertyNameProjection> getByUnitIdAndUserIdIsTenant(Long unitId, long userId);

    @Query("SELECT u.id as unitId, u.propertyId as propertyId, u.ref as unitRef, p.name as propertyName FROM Unit u JOIN Property p ON u.propertyId=p.id " +
            "WHERE u.id=:unitId AND u.active AND p.active AND (EXISTS (SELECT 1 FROM UnitTenant ut WHERE ut.unitId=u.id AND ut.userId=:userId AND ut.active) " +
            "OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active))")
    Optional<PropertyIdUnitRefPropertyNameProjection> getByUnitIdAndUserIdIsResident(Long unitId, long userId);

    @Query("SELECT u.id as unitId, u.propertyId as propertyId, u.ref as unitRef, p.name as propertyName  FROM UnitTenant ut JOIN Unit u ON ut.unitId = u.id JOIN Property p ON u.propertyId=p.id WHERE ut.userId=:userId AND ut.active")
    List<PropertyIdUnitRefPropertyNameProjection> getAllByUnitIdAndUserIdIsTenant(long userId);

    @Query("SELECT u.id as unitId, u.propertyId as propertyId, u.ref as unitRef, p.name as propertyName " +
            "FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE u.active AND p.active AND (" +
            "EXISTS (SELECT 1 FROM UnitTenant ut WHERE ut.unitId=u.id AND ut.userId=:userId AND ut.active) OR " +
            "EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=p.id AND " +
            "(po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active)) " +
            "ORDER BY p.name,u.ref")
    List<PropertyIdUnitRefPropertyNameProjection> getAllByUserIdIsResident(long userId);

    @Query("SELECT u.id as unitId, u.ref as unitRef, p.id as propertyId, p.name as propertyName, " +
            "host.id as hostUserId, host.fullName as hostName FROM Unit u JOIN Property p ON u.propertyId=p.id " +
            "JOIN PropertyManager pm ON pm.propertyId=p.id JOIN UnitTenant ut ON ut.unitId=u.id " +
            "JOIN Users host ON host.id=ut.userId WHERE pm.userId=:guardUserId AND pm.roleName IN ('GUARD','SECURITY_SUPERVISOR') " +
            "AND pm.active AND p.active AND u.active AND ut.active AND host.active ORDER BY p.name,u.ref,host.fullName")
    List<GuardHostOptionProjection> findGuardHostOptions(long guardUserId);

    @Query("SELECT u.id as unitId, u.ref as unitRef, p.id as propertyId, p.name as propertyName, " +
            "host.id as hostUserId, host.fullName as hostName FROM Unit u JOIN Property p ON u.propertyId=p.id " +
            "JOIN PropertyManager pm ON pm.propertyId=p.id JOIN PropertyOwnership ownership ON ownership.unitId=u.id " +
            "JOIN Users host ON host.id=ownership.homeownerUserId WHERE pm.userId=:guardUserId AND pm.roleName IN ('GUARD','SECURITY_SUPERVISOR') " +
            "AND pm.active AND p.active AND u.active AND ownership.active AND host.active ORDER BY p.name,u.ref,host.fullName")
    List<GuardHostOptionProjection> findGuardHomeownerOptions(long guardUserId);

    @Query("SELECT coalesce(count(u), 0) FROM Unit u JOIN Property p ON u.propertyId=p.id WHERE p.active AND u.active AND p.createdBy=:userId")
    int countUnitsByLandlord(long userId);

    @Query("SELECT COALESCE(COUNT(u), 0) FROM Unit u JOIN PropertyManager pm ON u.propertyId=pm.propertyId " +
            "WHERE u.occupied AND pm.userId=:userId AND pm.active AND u.active AND pm.roleName='PROPERTY_MANAGER'")
    int countOccupiedUnitsByPropertyManager(long userId);

    @Query("SELECT COALESCE(COUNT(u), 0) FROM Unit u JOIN PropertyManager pm ON u.propertyId=pm.propertyId " +
            "WHERE pm.userId=:userId AND pm.active AND u.active AND pm.roleName='PROPERTY_MANAGER'")
    int countAllUnitsByPropertyManager(long userId);
}
