package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.KycDocumentRepo;
import org.pms.silverocean.database.pms.KycMatrixReleaseRepo;
import org.pms.silverocean.database.pms.KycMatrixRequirementRepo;
import org.pms.silverocean.database.pms.entities.KycDocument;
import org.pms.silverocean.database.pms.entities.KycMatrixRelease;
import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.users.ProfileType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceKycGateTest {
    @Mock KycMatrixReleaseRepo releases;
    @Mock KycMatrixRequirementRepo requirements;
    @Mock KycDocumentRepo documents;
    @Mock org.pms.silverocean.database.pms.SokoRiderCredentialRepo riderCredentials;
    @Mock org.pms.silverocean.database.pms.ProviderDocumentRepo providerDocuments;
    MarketplaceKycGate gate;

    @BeforeEach void setup(){gate=new MarketplaceKycGate(releases,requirements,documents,riderCredentials,providerDocuments);}

    @Test void expiredEvidenceDoesNotSatisfyMandatoryRiderRequirement(){
        stubRequirement();KycDocument document=verifiedGoodConduct();document.setExpiresAt(ZonedDateTime.now().minusDays(1));
        when(documents.findByUserIdAndActiveTrueAndStatusOrderByCreatedOnDesc(9L,"VERIFIED")).thenReturn(List.of(document));
        assertThrows(PMSCustomException.class,()->gate.require(9L,"PROVIDER_TYPE","DELIVERY_RIDER",ProfileType.INDIVIDUAL));
    }

    @Test void supplementalRiderEvidenceIsReusedWithoutAnotherCommonKycUpload(){
        stubRequirement();var credential=new org.pms.silverocean.database.pms.entities.SokoRiderCredential();
        credential.setDocumentType("GOOD_CONDUCT_CERTIFICATE");credential.setStatus("VERIFIED");credential.setExpiresAt(ZonedDateTime.now().plusDays(30));
        when(riderCredentials.findAllByUserIdAndStatusAndActiveTrue(9L,"VERIFIED")).thenReturn(List.of(credential));
        assertDoesNotThrow(()->gate.require(9L,"PROVIDER_TYPE","DELIVERY_RIDER",ProfileType.INDIVIDUAL));
    }

    @Test void expiredSupplementalEvidenceDoesNotSatisfyRiderRequirement(){
        stubRequirement();var credential=new org.pms.silverocean.database.pms.entities.SokoRiderCredential();
        credential.setDocumentType("GOOD_CONDUCT_CERTIFICATE");credential.setStatus("VERIFIED");credential.setExpiresAt(ZonedDateTime.now().minusDays(1));
        when(riderCredentials.findAllByUserIdAndStatusAndActiveTrue(9L,"VERIFIED")).thenReturn(List.of(credential));
        assertThrows(PMSCustomException.class,()->gate.require(9L,"PROVIDER_TYPE","DELIVERY_RIDER",ProfileType.INDIVIDUAL));
    }

    @Test void expiredPassportCannotSuppressMachineEvaluatedIdentityBackRequirement(){
        KycMatrixRelease release=new KycMatrixRelease();release.setId(1L);
        when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED")).thenReturn(Optional.of(release));
        KycMatrixRequirement row=new KycMatrixRequirement();row.setObligation("CONDITIONAL");row.setProfileScope("BOTH");row.setConditionRule("NON_PASSPORT_IDENTITY");row.setRequirementLabel("Identity back");row.setAcceptedDocumentTypes("NATIONAL_ID_BACK");
        when(requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(1L,"PROVIDER_TYPE","SOKO_MERCHANT")).thenReturn(List.of(row));
        KycDocument passport=new KycDocument();passport.setDocumentType("PASSPORT");passport.setExpiresAt(ZonedDateTime.now().minusDays(1));
        when(documents.findByUserIdAndActiveTrueAndStatusOrderByCreatedOnDesc(9L,"VERIFIED")).thenReturn(List.of(passport));
        assertThrows(PMSCustomException.class,()->gate.require(9L,"PROVIDER_TYPE","SOKO_MERCHANT",ProfileType.INDIVIDUAL));
    }

    @Test void legacyVerifiedServiceEvidenceSatisfiesCanonicalMatrixRequirement(){
        KycMatrixRelease release=new KycMatrixRelease();release.setId(1L);
        when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED")).thenReturn(Optional.of(release));
        KycMatrixRequirement row=new KycMatrixRequirement();row.setObligation("MANDATORY");row.setProfileScope("BOTH");row.setRequirementLabel("Business registration");row.setAcceptedDocumentTypes("BUSINESS_REGISTRATION_CERTIFICATE");
        when(requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(1L,"SERVICE_CATEGORY","4")).thenReturn(List.of(row));
        var old=new org.pms.silverocean.database.pms.entities.ProviderDocument();old.setDocumentType("BUSINESS_REGISTRATION");
        when(providerDocuments.findVerifiedEvidence(9L,4L)).thenReturn(List.of(old));
        assertDoesNotThrow(()->gate.require(9L,"SERVICE_CATEGORY","4",ProfileType.COMPANY));
    }

    @Test void currentApprovedEvidenceIsReused(){
        stubRequirement();KycDocument document=verifiedGoodConduct();document.setExpiresAt(ZonedDateTime.now().plusDays(30));
        when(documents.findByUserIdAndActiveTrueAndStatusOrderByCreatedOnDesc(9L,"VERIFIED")).thenReturn(List.of(document));
        assertDoesNotThrow(()->gate.require(9L,"PROVIDER_TYPE","DELIVERY_RIDER",ProfileType.INDIVIDUAL));
    }

    private void stubRequirement(){KycMatrixRelease release=new KycMatrixRelease();release.setId(1L);when(releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED")).thenReturn(Optional.of(release));KycMatrixRequirement row=new KycMatrixRequirement();row.setActive(true);row.setObligation("MANDATORY");row.setProfileScope("INDIVIDUAL");row.setRequirementLabel("Certificate of good conduct");row.setAcceptedDocumentTypes("GOOD_CONDUCT_CERTIFICATE");when(requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(1L,"PROVIDER_TYPE","DELIVERY_RIDER")).thenReturn(List.of(row));}
    private KycDocument verifiedGoodConduct(){KycDocument document=new KycDocument();document.setActive(true);document.setStatus("VERIFIED");document.setDocumentType("GOOD_CONDUCT_CERTIFICATE");document.setCreatedOn(ZonedDateTime.now().minusDays(10));return document;}
}
