package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthAssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WealthAssetTypeRepo extends JpaRepository<WealthAssetType,Long>{
 List<WealthAssetType> findAllByActiveTrueOrderByDisplayOrderAscLabelAsc();
 List<WealthAssetType> findAllByOrderByDisplayOrderAscLabelAsc();
 Optional<WealthAssetType> findByCodeIgnoreCase(String code);
 boolean existsByCodeIgnoreCase(String code);
}
