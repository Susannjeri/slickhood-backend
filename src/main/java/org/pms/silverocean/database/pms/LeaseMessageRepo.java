package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.LeaseMessage;
import org.pms.silverocean.service.lease.wrappers.LeaseMessageDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseMessageRepo extends JpaRepository<LeaseMessage, Long> {
    @Query("""
                SELECT new org.pms.silverocean.service.lease.wrappers.LeaseMessageDTO(
                    lm.id,
                    lm.message,
                    lm.createdOn,
                    u.fullName,
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
                FROM LeaseMessage lm
                JOIN Users u ON u.id = lm.createdBy
                JOIN Lease l ON l.id = lm.leaseId
                JOIN UnitTenant ut ON ut.id = l.tenantId
                WHERE lm.leaseId = :leaseId
            """)
    Page<LeaseMessageDTO> findLeaseMessageDTOsByLeaseId(Pageable pageable, @Param("leaseId") long leaseId);
}
