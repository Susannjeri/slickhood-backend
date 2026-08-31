package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthObligation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthObligationRepo extends JpaRepository<WealthObligation,Long>{
 List<WealthObligation> findAllByAssetIdInAndActiveTrueOrderByDueDateAsc(List<Long> assetIds);
 List<WealthObligation> findAllByAssetIdAndActiveTrueOrderByDueDateAsc(long assetId);
 Optional<WealthObligation> findByIdAndAssetIdInAndActiveTrue(long id,List<Long> assetIds);
}
