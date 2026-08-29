package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.CommunityFundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityFundTransactionRepo extends JpaRepository<CommunityFundTransaction,Long> {
    List<CommunityFundTransaction> findByFundIdAndActiveTrueOrderByOccurredAtDesc(Long fundId);
    boolean existsByEventKey(String eventKey);
}
