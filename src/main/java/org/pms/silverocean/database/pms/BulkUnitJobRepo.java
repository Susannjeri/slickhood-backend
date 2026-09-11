package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.BulkUnitJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface BulkUnitJobRepo extends JpaRepository<BulkUnitJob, Long> {
    Optional<BulkUnitJob> findByIdAndActiveTrueAndCompletedFalse(long jobId);

    @Query("SELECT j FROM BulkUnitJob j WHERE  j.active AND NOT j.completed")
    Set<BulkUnitJob> findIncompleteJobs();

    Page<BulkUnitJob> findByCreatedBy(Pageable pageable, long createdBy);

    Optional<BulkUnitJob> findByIdAndCreatedBy(long id, long createdBy);

    /**
     * Pending duplicate jobs are reservations too.  Counting them with the
     * existing inventory prevents two quick submissions from exceeding the
     * subscription unit quota before the asynchronous workers finish.
     */
    @Query("SELECT COALESCE(SUM(j.count), 0) FROM BulkUnitJob j JOIN Unit u ON u.id=j.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE j.active AND NOT j.completed AND p.createdBy=:ownerId")
    long sumPendingCountsByPropertyOwner(long ownerId);

    @Query("SELECT COALESCE(SUM(j.count), 0) FROM BulkUnitJob j JOIN Unit u ON u.id=j.unitId JOIN Property p ON p.id=u.propertyId " +
            "WHERE j.active AND NOT j.completed AND p.createdBy=:ownerId AND u.leaseMode=:leaseMode")
    long sumPendingCountsByPropertyOwnerAndLeaseMode(long ownerId, String leaseMode);

    Integer countBulkUnitJobByCreatedByAndActiveTrueAndCompletedFalse(long createdBy);
}
