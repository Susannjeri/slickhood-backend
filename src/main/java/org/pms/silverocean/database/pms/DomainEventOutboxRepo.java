package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.DomainEventOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface DomainEventOutboxRepo extends JpaRepository<DomainEventOutbox,Long>{
    boolean existsByDedupeKey(String dedupeKey);
    long countByStatusInAndActiveTrue(Collection<String> statuses);
    long countByStatusAndActiveTrue(String status);
    Page<DomainEventOutbox> findByStatusInAndActiveTrueOrderByCreatedOnDesc(Collection<String> statuses, Pageable pageable);
    @Query("SELECT e.id FROM DomainEventOutbox e WHERE e.active AND e.status IN ('PENDING','FAILED') AND e.nextAttemptAt<=:now ORDER BY e.createdOn")
    List<Long> findDispatchCandidates(@Param("now") LocalDateTime now, Pageable pageable);
    @Modifying
    @Query("UPDATE DomainEventOutbox e SET e.status='PROCESSING',e.processingStartedAt=:now WHERE e.id=:id AND e.active AND e.status IN ('PENDING','FAILED') AND e.nextAttemptAt<=:now")
    int claim(@Param("id") long id,@Param("now") LocalDateTime now);
    @Modifying
    @Query("UPDATE DomainEventOutbox e SET e.status='FAILED',e.processingStartedAt=null,e.nextAttemptAt=:now,e.lastError='Recovered stale processing claim' WHERE e.active AND e.status='PROCESSING' AND e.processingStartedAt<:staleBefore")
    int recoverStale(@Param("staleBefore") LocalDateTime staleBefore,@Param("now") LocalDateTime now);
}
