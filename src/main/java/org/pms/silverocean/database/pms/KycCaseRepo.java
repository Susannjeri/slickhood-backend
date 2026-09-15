package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.KycCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Collection;

import java.util.Optional;

public interface KycCaseRepo extends JpaRepository<KycCase, Long> {
    @Query("SELECT k FROM KycCase k WHERE k.active AND k.createdOn>=:start AND k.createdOn<:end AND (:privileged=true OR k.userId=:userId) ORDER BY k.createdOn DESC")
    List<KycCase> findForReport(long userId,boolean privileged,ZonedDateTime start,ZonedDateTime end,Pageable pageable);
    Optional<KycCase> findByUserId(long userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KycCase k where k.userId=:userId and k.active=true")
    Optional<KycCase> findByUserIdForUpdate(long userId);
    List<KycCase> findByStatusInAndActiveTrueOrderBySubmittedAtAsc(Collection<String> statuses);
}
