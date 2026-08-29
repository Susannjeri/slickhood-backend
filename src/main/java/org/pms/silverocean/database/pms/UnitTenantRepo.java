package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.service.lease.wrappers.LeaseIdTenantSignDateDTO;
import org.pms.silverocean.service.lease.wrappers.TenancyProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UnitTenantRepo extends JpaRepository<UnitTenant, Long> {

    @Query("SELECT ut.id as tenancyId, l.id as leaseId, ut.id as unitID, u.ref as unitRef, p.name as propertyName, ut.leaseAccepted as leaseAccepted, ut.createdOn as requestedOn " +
            " FROM UnitTenant ut JOIN Lease l ON ut.id=l.tenantId JOIN Unit u ON ut.unitId=u.id JOIN Property p ON u.propertyId=p.id WHERE p.active AND u.active AND l.active" +
            " AND ut.active AND ut.userId=:userId")
    List<TenancyProjection> findByUserIdAndActiveTrue(long userId);

    @Query("SELECT new org.pms.silverocean.service.lease.wrappers.LeaseIdTenantSignDateDTO(l.id, l.tenantSignedDate, l.managerSignedDate)" +
            " FROM UnitTenant ut JOIN Lease l ON l.tenantId=ut.id WHERE ut.unitId=:unitId AND ut.active AND ut.leaseAccepted AND l.signed AND l.active")
    Optional<LeaseIdTenantSignDateDTO> getActiveSignedLeaseIdByUnitId(long unitId);

    @Query("SELECT new org.pms.silverocean.service.lease.wrappers.LeaseIdTenantSignDateDTO(l.id, l.tenantSignedDate, l.managerSignedDate)" +
            " FROM UnitTenant ut JOIN Lease l ON l.tenantId=ut.id WHERE ut.userId=:userId AND ut.unitId=:unitId AND ut.active AND l.active AND l.signed is false")
    Optional<LeaseIdTenantSignDateDTO> getUnsignedActiveLeaseIdByTenantsUserIdAndUnitId(long userId, long unitId);
    @Modifying
    @Query("UPDATE UnitTenant ut set ut.active=false WHERE ut.unitId=:unitId AND ut.active=true AND ut.leaseAccepted is false AND ut.id NOT IN (SELECT l.tenantId FROM Lease l WHERE l.id=:leaseId)")
    void deactivateUnsignedUnitTenantsIdByUnitId(long unitId, long leaseId);

    Optional<UnitTenant> findByIdAndActiveTrue(long tenantId);

    @Query("SELECT ut FROM UnitTenant ut JOIN Unit u ON u.id=ut.unitId WHERE u.propertyId=:propertyId AND u.active AND ut.active")
    List<UnitTenant> findActiveByPropertyId(long propertyId);

    @Query("SELECT u FROM UnitTenant ut JOIN Unit u ON ut.unitId=u.id WHERE ut.id=:tenantId")
    Optional<Unit> findUnitByTenantId(long tenantId);

    @Query("SELECT coalesce(count(u), 0) FROM Unit u JOIN UnitTenant ut ON u.id=ut.unitId JOIN Lease l ON ut.id=l.tenantId WHERE ut.active AND u.active AND l.signed AND l.active AND u.createdBy=:userId")
    int countTenantsByLandlord(long userId);

    @Query("SELECT coalesce(count(ut), 0) FROM PropertyManager pm JOIN Unit u ON pm.propertyId=u.propertyId JOIN UnitTenant ut " +
            "ON u.id=ut.unitId JOIN Lease l ON ut.id=l.tenantId " +
            "WHERE ut.active AND u.active AND NOT l.signed AND l.active AND pm.userId=:userId AND pm.active AND pm.roleName='PROPERTY_MANAGER'")
    int countUnsignedLeasesByPropertyManager(long userId);

}
