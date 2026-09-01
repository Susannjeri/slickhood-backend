package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthVaultDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthVaultDocumentRepo extends JpaRepository<WealthVaultDocument,Long>{
 List<WealthVaultDocument> findTop200ByAssetIdAndActiveTrueOrderByCreatedOnDesc(long assetId);
 List<WealthVaultDocument> findTop200ByOwnerUserIdAndActiveTrueOrderByCreatedOnDesc(long ownerUserId);
 Optional<WealthVaultDocument> findByIdAndOwnerUserIdAndActiveTrue(long id,long ownerUserId);
 boolean existsByOwnerUserIdAndCategoryAndActiveTrue(long ownerUserId,String category);
}
