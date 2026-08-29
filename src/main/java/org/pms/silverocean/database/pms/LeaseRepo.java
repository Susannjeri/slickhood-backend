package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.service.lease.wrappers.LeaseContextDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseDTO;
import org.pms.silverocean.service.payment.invoice.wrappers.ProcessLeaseInvoiceDTO;
import org.pms.silverocean.service.reports.LeaseExpiryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.time.LocalDate;

public interface LeaseRepo extends JpaRepository<Lease, Long> {
    @Query("SELECT l.id AS leaseId,u.propertyId AS propertyId,u.id AS unitId,u.ref AS unitRef,ut.userId AS tenantUserId," +
            "l.moveInDate AS moveInDate,l.moveOutDate AS moveOutDate,l.signed AS signed,l.selfRenew AS selfRenew," +
            "l.noticePeriodInMonths AS noticePeriodInMonths,l.currency AS currency,l.price AS price " +
            "FROM Lease l JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Unit u ON u.id=ut.unitId " +
            "WHERE l.active AND ut.active AND l.moveOutDate IS NOT NULL AND l.moveOutDate BETWEEN :from AND :to AND " +
            "(:privileged=true OR ut.userId=:userId OR u.createdBy=:userId OR u.propertyId IN " +
            "(SELECT pm.propertyId FROM PropertyManager pm WHERE pm.userId=:userId AND pm.active)) ORDER BY l.moveOutDate")
    List<LeaseExpiryProjection> findExpiringForReport(long userId, boolean privileged, LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lease l JOIN UnitTenant ut ON l.tenantId=ut.id WHERE ut.userId=:userId AND l.id=:leaseId AND l.active AND ut.active")
    Optional<Lease> findByIdAndTenant(long leaseId, long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lease l LEFT JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Unit u ON u.id=ut.unitId LEFT JOIN" +
            " PropertyManager pm ON pm.propertyId=u.propertyId WHERE (ut.userId=:tenantOrStaffId OR pm.userId=:tenantOrStaffId OR u.createdBy=:tenantOrStaffId) AND l.id=:leaseId AND l.active AND ut.active")
    Optional<Lease> findByIdAndTenantOrStaff(long leaseId, long tenantOrStaffId);


    @Query("""
                SELECT new org.pms.silverocean.service.lease.wrappers.LeaseContextDTO(
                    u.id,
                    ut.userId,
                    p.name,
                   p.createdBy,
                     CASE
                        WHEN ut.userId = u.id THEN 'TENANT'
                        WHEN EXISTS (
                            SELECT 1 FROM PropertyManager pm
                            JOIN Property p ON p.id = pm.propertyId
                            JOIN Unit un ON un.propertyId = p.id
                            WHERE un.id = ut.unitId
                            AND pm.userId = u.id
                            AND pm.active = true AND p.active = true AND un.active = true
                        ) THEN 'PROPERTY_MANAGER'
                        ELSE 'LANDLORD'
                    END
                )
                FROM Lease l
                JOIN UnitTenant ut ON ut.id = l.tenantId
                JOIN Unit u ON u.id = ut.unitId
                JOIN Property p ON p.id = u.propertyId
                WHERE l.id = :leaseId
                AND (ut.userId = :currentUserId OR EXISTS (
                    SELECT 1 FROM PropertyManager pm
                    WHERE pm.propertyId = p.id AND pm.userId = :currentUserId AND pm.active = true
                ) OR p.createdBy = :currentUserId)
            """)
    Optional<LeaseContextDTO> getLeaseProcessingContext(@Param("leaseId") long leaseId, @Param("currentUserId") long currentUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lease l JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Unit u ON u.id=ut.unitId LEFT JOIN" +
            " PropertyManager pm ON pm.propertyId=u.propertyId WHERE (pm.userId=:staffId OR u.createdBy=:staffId) AND l.id=:leaseId AND l.active AND ut.active")
    Optional<Lease> findByIdAndStaff(long leaseId, long staffId);

    @Query("SELECT l FROM Lease l JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Unit u ON u.id=ut.unitId JOIN Property p ON p.id=u.propertyId WHERE l.id=:leaseId AND l.active AND ut.active AND p.createdBy=:ownerId")
    Optional<Lease> findByIdAndOwner(long leaseId, long ownerId);

    @Query("SELECT l FROM Lease l JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Unit u ON u.id=ut.unitId JOIN PropertyManager pm ON pm.propertyId=u.propertyId WHERE l.id=:leaseId AND l.active AND ut.active AND pm.active AND pm.userId=:managerId AND pm.roleName=:roleName")
    Optional<Lease> findByIdAndManagerRole(long leaseId, long managerId, String roleName);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lease l set l.active=false, l.lastModifiedDate = CURRENT_TIMESTAMP WHERE l.tenantId IN (SELECT ut.id FROM UnitTenant ut WHERE ut.unitId=:unitId AND ut.active=true AND ut.leaseAccepted is false) AND l.id <> :leaseId")
    void deactivateUnsignedActiveLeaseIdByUnitId(long unitId, long leaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT new org.pms.silverocean.service.payment.invoice.wrappers.ProcessLeaseInvoiceDTO(l.id, l.leaseMode, l.leaseDate, l.nextPaymentDate, l.price, l.currency, l.charges, ut.unitId, ut.userId) from Lease l JOIN UnitTenant ut ON ut.id=l.tenantId WHERE l.paymentDue AND l.active AND l.nextPaymentDate <= CURRENT_DATE")
    Slice<ProcessLeaseInvoiceDTO> findLeasePaymentsDueToday(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lease l SET l.nextPaymentDate = l.nextPaymentDate + 1 MONTH WHERE l.id IN :ids")
    void incrementNextPaymentDate(Set<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Lease l SET l.paymentDue = false WHERE l.id IN :ids")
    void deactivatePaymentDue(Set<Long> ids);


    @Query("SELECT new org.pms.silverocean.service.lease.wrappers.LeaseDTO(l, u.fullName, owner.fullName) FROM Lease l " +
            "JOIN UnitTenant ut ON l.tenantId=ut.id JOIN Users u ON ut.userId=u.id JOIN Users owner ON l.signedByManagerId=owner.id")
    Page<LeaseDTO> findAllLease(Pageable pageable);
}
