package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InsuranceQuoteRepo extends JpaRepository<InsuranceQuote,Long>{
 List<InsuranceQuote> findAllByCaseIdAndActiveTrueOrderByTotalPremiumAsc(long caseId);
 List<InsuranceQuote> findAllByCaseIdInAndActiveTrueOrderByCaseIdAscTotalPremiumAsc(Collection<Long> caseIds);
 Optional<InsuranceQuote> findByIdAndCaseIdAndActiveTrue(long id,long caseId);
}
