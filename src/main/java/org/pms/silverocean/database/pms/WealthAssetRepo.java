package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthAssetRepo extends JpaRepository<WealthAsset,Long>{
 List<WealthAsset> findAllByOwnerUserIdAndActiveTrueOrderByName(long ownerUserId);
 Optional<WealthAsset> findByIdAndOwnerUserIdAndActiveTrue(long id,long ownerUserId);
}
