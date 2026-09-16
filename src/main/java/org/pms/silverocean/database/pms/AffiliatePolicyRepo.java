package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.AffiliatePolicy;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AffiliatePolicyRepo extends JpaRepository<AffiliatePolicy,Long>{
 Optional<AffiliatePolicy> findByPolicyKey(String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from AffiliatePolicy p where p.policyKey='GLOBAL'") Optional<AffiliatePolicy> lockGlobal();
}
