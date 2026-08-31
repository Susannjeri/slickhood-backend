package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SaleMilestone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleMilestoneRepo extends JpaRepository<SaleMilestone,Long> {
    Page<SaleMilestone> findAllBySaleIdOrderByOccurredAtAsc(long saleId, Pageable pageable);
    boolean existsBySaleIdAndMilestoneTypeAndStatus(long saleId,String type,String status);
}
