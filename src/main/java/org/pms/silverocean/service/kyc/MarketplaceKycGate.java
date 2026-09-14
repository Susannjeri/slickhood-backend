package org.pms.silverocean.service.kyc;

import lombok.RequiredArgsConstructor;
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

    public void require(long userId,String scopeType,String scopeKey,ProfileType profileType){
        var live=releases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED").orElse(null);
        if(live==null)return;
        List<KycMatrixRequirement> applicable=requirements.findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(
                live.getId(),scopeType.trim().toUpperCase(Locale.ROOT),scopeKey.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]","_"));
        List<KycDocument> evidence=documents.findByUserIdAndActiveTrueAndStatusOrderByCreatedOnDesc(userId,DocumentStatus.VERIFIED.name());
        List<String> missing=applicable.stream().filter(row->"MANDATORY".equals(row.getObligation())).filter(row->profileMatches(row,profileType)).filter(row->!satisfied(row,evidence)).map(KycMatrixRequirement::getRequirementLabel).toList();
        if(!missing.isEmpty())throw new PMSCustomException(ResponseCode.KYC_MISSING_DOCUMENTS,"Complete these KYC requirements before continuing: "+String.join(", ",missing)+".");
    }

    private boolean profileMatches(KycMatrixRequirement row,ProfileType profileType){return "BOTH".equals(row.getProfileScope())||profileType.name().equals(row.getProfileScope());}
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
