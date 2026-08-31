package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.InsuranceActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InsuranceActivityRepo extends JpaRepository<InsuranceActivity,Long>{
 List<InsuranceActivity> findAllByCaseIdOrderByCreatedOnAsc(long caseId);
 List<InsuranceActivity> findAllByClaimIdOrderByCreatedOnAsc(long claimId);
}
