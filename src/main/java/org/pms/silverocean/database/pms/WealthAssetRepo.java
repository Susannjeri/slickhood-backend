package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface WealthAssetRepo extends JpaRepository<WealthAsset,Long>{
 List<WealthAsset> findAllByOwnerUserIdAndActiveTrueOrderByName(long ownerUserId);
 Optional<WealthAsset> findByIdAndOwnerUserIdAndActiveTrue(long id,long ownerUserId);
 Optional<WealthAsset> findByOwnerUserIdAndPropertyIdAndActiveTrue(long ownerUserId,long propertyId);
 List<WealthAsset> findAllByPricingModeAndActiveTrue(String pricingMode);
 Page<WealthAsset> findAllByPricingModeAndActiveTrue(String pricingMode,Pageable pageable);
}
