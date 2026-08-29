package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFundExpenditure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityFundExpenditureRepo extends JpaRepository<CommunityFundExpenditure,Long> {
    List<CommunityFundExpenditure> findByFundIdAndActiveTrueOrderByCreatedOnDesc(Long fundId);
}
