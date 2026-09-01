package org.pms.silverocean.service.wealth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.entities.WealthVaultDocument;

import static org.assertj.core.api.Assertions.assertThat;

class WealthModelsSecurityTest {
    @Test void assetApiModelDoesNotExposeOwnershipOrAuditInternals() throws Exception {
        var asset=new org.pms.silverocean.database.pms.entities.WealthAsset();
        asset.setId(2L);asset.setOwnerUserId(77L);asset.setCreatedBy(88L);asset.setAssetType("CASH");
        asset.setName("Emergency fund");asset.setCurrency("KES");asset.setCurrentValue(java.math.BigDecimal.TEN);
        asset.setValuationDate(java.time.LocalDate.now());asset.setStatus("ACTIVE");asset.setPricingMode("MANUAL");
        String json=new ObjectMapper().findAndRegisterModules().writeValueAsString(new WealthModels.AssetView(asset));
        assertThat(json).contains("Emergency fund").doesNotContain("ownerUserId","createdBy","lastModifiedDate");
    }

    @Test void vaultApiModelNeverSerializesThePrivateStorageReference() throws Exception {
        WealthVaultDocument document=new WealthVaultDocument();
        document.setId(9L);document.setAssetId(4L);document.setCategory("WILL");
        document.setDisplayName("will.pdf");document.setContentType("application/pdf");
        document.setFileSize(128);document.setChecksumSha256("abc");
        document.setFileRef("wealth/22/vault/private-object.pdf");

        String json=new ObjectMapper().writeValueAsString(
                new WealthModels.VaultDocumentView(new WealthModels.VaultDocumentMetadata(document),null));

        assertThat(json).contains("will.pdf").doesNotContain("fileRef","private-object");
    }
}
