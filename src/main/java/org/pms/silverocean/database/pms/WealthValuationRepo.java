package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WealthValuationRepo extends JpaRepository<WealthValuation,Long>{List<WealthValuation> findAllByAssetIdAndActiveTrueOrderByValuationDateDesc(long assetId);}
