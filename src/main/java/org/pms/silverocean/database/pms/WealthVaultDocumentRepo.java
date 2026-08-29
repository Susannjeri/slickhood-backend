package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.WealthVaultDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WealthVaultDocumentRepo extends JpaRepository<WealthVaultDocument,Long>{List<WealthVaultDocument> findAllByAssetIdAndActiveTrueOrderByCreatedOnDesc(long assetId);Optional<WealthVaultDocument> findByIdAndAssetIdInAndActiveTrue(long id,List<Long> assetIds);}
