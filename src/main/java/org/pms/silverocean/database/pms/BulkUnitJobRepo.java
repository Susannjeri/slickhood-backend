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

    Integer countBulkUnitJobByCreatedByAndActiveTrueAndCompletedFalse(long createdBy);
}
