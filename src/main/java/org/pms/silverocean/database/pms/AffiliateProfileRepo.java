package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.AffiliateProfile;import org.springframework.data.jpa.repository.JpaRepository;import java.util.Optional;
public interface AffiliateProfileRepo extends JpaRepository<AffiliateProfile,Long>{Optional<AffiliateProfile> findByUserIdAndActiveTrue(long userId);Optional<AffiliateProfile> findByReferralCodeAndActiveTrue(String code);boolean existsByReferralCode(String code);}
