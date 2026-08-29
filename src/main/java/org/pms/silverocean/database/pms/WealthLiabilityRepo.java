package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthLiability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WealthLiabilityRepo extends JpaRepository<WealthLiability,Long>{List<WealthLiability> findAllByAssetIdInAndActiveTrue(List<Long> assetIds);}
