package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.EstateBudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EstateBudgetLineRepo extends JpaRepository<EstateBudgetLine, Long> {
    List<EstateBudgetLine> findAllByBudgetIdAndActiveTrueOrderById(long budgetId);
    List<EstateBudgetLine> findAllByBudgetIdInAndActiveTrueOrderByBudgetIdAscIdAsc(Collection<Long> budgetIds);
}
