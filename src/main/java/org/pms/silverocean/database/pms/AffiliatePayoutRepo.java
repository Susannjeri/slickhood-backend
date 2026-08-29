package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.AffiliatePayout;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface AffiliatePayoutRepo extends JpaRepository<AffiliatePayout,Long>{List<AffiliatePayout> findAllByAffiliateUserIdAndActiveTrueOrderByCreatedOnDesc(long id);List<AffiliatePayout> findAllByActiveTrueOrderByCreatedOnDesc();boolean existsByAffiliateUserIdAndStatusAndActiveTrue(long id,String status);}
