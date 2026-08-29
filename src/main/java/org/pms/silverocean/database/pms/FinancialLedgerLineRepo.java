package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.FinancialLedgerLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.ZonedDateTime;
import java.util.List;

public interface FinancialLedgerLineRepo extends JpaRepository<FinancialLedgerLine, Long> {
    @Query("SELECT l FROM FinancialLedgerLine l WHERE l.createdOn>=:start AND l.createdOn<:end AND " +
            "(:privileged=true OR l.userId=:userId OR l.propertyId IN " +
            "(SELECT pm.propertyId FROM PropertyManager pm WHERE pm.userId=:userId AND pm.active)) " +
            "ORDER BY l.createdOn DESC,l.journalId DESC,l.lineNumber")
    List<FinancialLedgerLine> findForStatement(long userId, boolean privileged, ZonedDateTime start,
                                                ZonedDateTime end, Pageable pageable);
}
