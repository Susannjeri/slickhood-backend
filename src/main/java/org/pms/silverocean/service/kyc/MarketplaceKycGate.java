package org.pms.silverocean.service.kyc;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.database.pms.KycDocumentRepo;
import org.pms.silverocean.database.pms.KycMatrixReleaseRepo;
import org.pms.silverocean.database.pms.KycMatrixRequirementRepo;
import org.pms.silverocean.database.pms.entities.KycDocument;
import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketplaceKycGate {
    private final KycMatrixReleaseRepo releases;
    private final KycMatrixRequirementRepo requirements;
    private final KycDocumentRepo documents;
    private final org.pms.silverocean.database.pms.SokoRiderCredentialRepo riderCredentials;
    private final org.pms.silverocean.database.pms.ProviderDocumentRepo providerDocuments;

    public void require(long userId,String scopeType,String scopeKey,ProfileType profileType){
        List<String> missing=missingRequirements(userId,scopeType,scopeKey,profileType);
        if(!missing.isEmpty())throw new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS,"Complete these KYC requirements before continuing: "+String.join(", ",missing)+".");
    }

    public List<String> missingRequirements(long userId,String scopeType,String scopeKey,ProfileType profileType){
        var live=releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED").orElse(null);
        if(live==null)return List.of();
        List<KycMatrixRequirement> applicable=requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(
                live.getId(),scopeType.trim().toUpperCase(Locale.ROOT),scopeKey.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]","_"));
        List<KycDocument> evidence=new java.util.ArrayList<>(currentVerifiedDocuments(userId));
        if("SERVICE_CATEGORY".equalsIgnoreCase(scopeType)||("PROVIDER_TYPE".equalsIgnoreCase(scopeType)&&"SERVICE_PROVIDER".equalsIgnoreCase(scopeKey))){
            Long categoryId="SERVICE_CATEGORY".equalsIgnoreCase(scopeType)?Long.valueOf(scopeKey):null;
            providerDocuments.findVerifiedEvidence(userId,categoryId).forEach(provider->{
                KycDocument d=new KycDocument();d.setDocumentType(provider.getDocumentType());d.setExpiresAt(provider.getExpiryDate());d.setCreatedOn(provider.getCreatedOn());evidence.add(d);
                String canonical=java.util.Map.of("BUSINESS_REGISTRATION","BUSINESS_REGISTRATION_CERTIFICATE","TAX_CERTIFICATE","KRA_PIN_CERTIFICATE","GOOD_CONDUCT","GOOD_CONDUCT_CERTIFICATE").get(provider.getDocumentType());
                if(canonical!=null){KycDocument mapped=new KycDocument();mapped.setDocumentType(canonical);mapped.setExpiresAt(provider.getExpiryDate());mapped.setCreatedOn(provider.getCreatedOn());evidence.add(mapped);}
            });
            List<KycDocument> aliases=new java.util.ArrayList<>();
            evidence.forEach(d->{String alias=java.util.Map.of("BUSINESS_REGISTRATION_CERTIFICATE","BUSINESS_REGISTRATION","KRA_PIN_CERTIFICATE","TAX_CERTIFICATE","GOOD_CONDUCT_CERTIFICATE","GOOD_CONDUCT").get(d.getDocumentType());if(alias!=null){KycDocument mapped=new KycDocument();mapped.setDocumentType(alias);mapped.setExpiresAt(d.getExpiresAt());mapped.setReverificationDueAt(d.getReverificationDueAt());mapped.setCreatedOn(d.getCreatedOn());mapped.setReviewedAt(d.getReviewedAt());aliases.add(mapped);}});
            if(evidence.stream().anyMatch(d->"NATIONAL_ID_FRONT".equals(d.getDocumentType()))&&evidence.stream().anyMatch(d->"NATIONAL_ID_BACK".equals(d.getDocumentType()))){evidence.stream().filter(d->"NATIONAL_ID_FRONT".equals(d.getDocumentType())).findFirst().ifPresent(d->{KycDocument mapped=new KycDocument();mapped.setDocumentType("NATIONAL_ID");mapped.setCreatedOn(d.getCreatedOn());mapped.setReviewedAt(d.getReviewedAt());mapped.setExpiresAt(d.getExpiresAt());mapped.setReverificationDueAt(d.getReverificationDueAt());aliases.add(mapped);});}
            evidence.addAll(aliases);
        }
        if("PROVIDER_TYPE".equalsIgnoreCase(scopeType)&&"DELIVERY_RIDER".equalsIgnoreCase(scopeKey)){
            riderCredentials.findAllByUserIdAndStatusAndActiveTrue(userId,"VERIFIED").forEach(credential->{
                KycDocument d=new KycDocument();d.setDocumentType(credential.getDocumentType());d.setExpiresAt(credential.getExpiresAt());d.setReviewedAt(credential.getReviewedAt());d.setCreatedOn(credential.getCreatedOn());evidence.add(d);
            });
            evidence.removeIf(d->d.getExpiresAt()!=null&&!d.getExpiresAt().isAfter(ZonedDateTime.now(ZoneId.of("UTC"))));
        }
        return applicable.stream().filter(row->profileMatches(row,profileType)).filter(row->required(row,profileType,evidence)).filter(row->!satisfied(row,evidence)).map(KycMatrixRequirement::getRequirementLabel).toList();
    }

    public List<KycDocument> currentVerifiedDocuments(long userId){
        ZonedDateTime now=ZonedDateTime.now(ZoneId.of("UTC"));
        return documents.findByUserIdAndActiveTrueAndStatusOrderByCreatedOnDesc(userId,DocumentStatus.VERIFIED.name()).stream()
                .filter(d->d.getExpiresAt()==null||d.getExpiresAt().isAfter(now))
                .filter(d->d.getReverificationDueAt()==null||d.getReverificationDueAt().isAfter(now)).toList();
    }

    public List<String> outstandingDocumentTypes(long userId,String scopeType,String scopeKey,ProfileType profileType){
        List<String> missing=missingRequirements(userId,scopeType,scopeKey,profileType);
        if(missing.isEmpty())return List.of();
        var live=releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED").orElse(null);
        if(live==null)return List.of();
        return requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(live.getId(),scopeType,scopeKey).stream()
                .filter(row->profileMatches(row,profileType)&&missing.contains(row.getRequirementLabel()))
                .flatMap(row->Arrays.stream(row.getAcceptedDocumentTypes().split(","))).map(String::trim).distinct().toList();
    }

    private boolean profileMatches(KycMatrixRequirement row,ProfileType profileType){return "BOTH".equals(row.getProfileScope())||profileType.name().equals(row.getProfileScope());}
    private boolean required(KycMatrixRequirement row,ProfileType profileType,List<KycDocument> evidence){
        if("MANDATORY".equals(row.getObligation()))return true;if(!"CONDITIONAL".equals(row.getObligation()))return false;
        String value=StringUtils.defaultString(row.getConditionValue()).trim().toUpperCase(Locale.ROOT);
        return switch(StringUtils.defaultString(row.getConditionRule())){
            case "NON_PASSPORT_IDENTITY"->evidence.stream().noneMatch(d->"PASSPORT".equals(d.getDocumentType()));
            case "PROFILE_IS"->profileType.name().equals(value);
            case "VERIFIED_DOCUMENT_PRESENT"->evidence.stream().anyMatch(d->value.equals(d.getDocumentType()));
            case "VERIFIED_DOCUMENT_MISSING"->evidence.stream().noneMatch(d->value.equals(d.getDocumentType()));
            default->false;
        };
    }
    private boolean satisfied(KycMatrixRequirement row,List<KycDocument> evidence){
        Set<String> accepted=Arrays.stream(row.getAcceptedDocumentTypes().split(",")).map(String::trim).collect(Collectors.toSet());
        ZonedDateTime now=ZonedDateTime.now(ZoneId.of("UTC"));
        return evidence.stream().filter(document->accepted.contains(document.getDocumentType())).anyMatch(document->{
            if(document.getExpiresAt()!=null&&!document.getExpiresAt().isAfter(now))return false;
            if(document.getReverificationDueAt()!=null&&!document.getReverificationDueAt().isAfter(now))return false;
            if(row.getValidityDays()!=null){ZonedDateTime basis=document.getReviewedAt()!=null?document.getReviewedAt():document.getCreatedOn();if(basis==null||!basis.plusDays(row.getValidityDays()).isAfter(now))return false;}
            return true;
        });
    }
}
