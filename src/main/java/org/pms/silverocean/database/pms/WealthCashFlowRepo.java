package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface WealthCashFlowRepo extends JpaRepository<WealthCashFlow,Long>{
 List<WealthCashFlow> findAllByAssetIdInAndActiveTrueAndEntryDateBetween(List<Long> assetIds,LocalDate from,LocalDate to);
 List<WealthCashFlow> findAllByAssetIdAndActiveTrueOrderByEntryDateDesc(long assetId);
 Optional<WealthCashFlow> findByIdAndAssetIdInAndActiveTrue(long id,List<Long> assetIds);
}
