package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthLiability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthLiabilityRepo extends JpaRepository<WealthLiability,Long>{
 List<WealthLiability> findAllByAssetIdInAndActiveTrue(List<Long> assetIds);
 List<WealthLiability> findAllByAssetIdAndActiveTrueOrderByIdDesc(long assetId);
 Optional<WealthLiability> findByIdAndAssetIdInAndActiveTrue(long id,List<Long> assetIds);
}
