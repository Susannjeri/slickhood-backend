package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFundExpenditure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityFundExpenditureRepo extends JpaRepository<CommunityFundExpenditure,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT e FROM CommunityFundExpenditure e WHERE e.id=:id AND e.active=true")
    java.util.Optional<CommunityFundExpenditure> findForUpdate(long id);
    List<CommunityFundExpenditure> findByFundIdAndActiveTrueOrderByCreatedOnDesc(Long fundId);
}
