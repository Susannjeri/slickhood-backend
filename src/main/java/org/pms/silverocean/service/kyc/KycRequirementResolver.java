package org.pms.silverocean.service.kyc;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.users.ProfileType;
import org.pms.silverocean.database.pms.KycMatrixReleaseRepo;
import org.pms.silverocean.database.pms.KycMatrixRequirementRepo;
import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class KycRequirementResolver {
    @Autowired(required = false) private KycMatrixReleaseRepo matrixReleases;
    @Autowired(required = false) private KycMatrixRequirementRepo matrixRequirements;
    public Set<KycRequirement> resolve(Set<PMSRole> roles) {
        return resolve(roles, ProfileType.INDIVIDUAL);
    }

    public Set<KycRequirement> resolve(Set<PMSRole> roles, ProfileType profileType) {
        Map<String, KycRequirement> requirements = new LinkedHashMap<>();
        add(requirements, "IDENTITY_FRONT", "Government identity document", true,
                Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT, KycDocumentType.ALIEN_ID_FRONT));
        add(requirements, "IDENTITY_BACK", "Back of identity document (not required for passports)", true,
                Set.of(KycDocumentType.NATIONAL_ID_BACK, KycDocumentType.ALIEN_ID_BACK));
        add(requirements, "SELFIE", "Live selfie", true, Set.of(KycDocumentType.SELFIE));

        if (profileType == ProfileType.COMPANY) {
            add(requirements, "ORGANIZATION_REGISTRATION", "Company or organization registration certificate", true,
                    Set.of(KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE, KycDocumentType.CR12));
        }

        if (roles.contains(PMSRole.LANDLORD) || roles.contains(PMSRole.HOMEOWNER)) {
            add(requirements, "OWNERSHIP", "Proof of property ownership", true,
                    Set.of(KycDocumentType.PROPERTY_OWNERSHIP_DOCUMENT));
        }
        if (roles.contains(PMSRole.ESTATE_MANAGER) || roles.contains(PMSRole.PROPERTY_MANAGER)) {
            add(requirements, "MANAGEMENT_AUTHORITY", "Management appointment or business registration", true,
                    Set.of(KycDocumentType.APPOINTMENT_LETTER, KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE));
        }
        if (roles.contains(PMSRole.SALES_AGENT)) {
            add(requirements, "SALES_AUTHORITY", "Sales appointment or professional certificate", true,
                    Set.of(KycDocumentType.APPOINTMENT_LETTER, KycDocumentType.PROFESSIONAL_CERTIFICATE));
        }
        if (roles.contains(PMSRole.SERVICE_PROVIDER)) {
            add(requirements, "GOOD_CONDUCT", "Certificate of good conduct (optional)", false,
                    Set.of(KycDocumentType.GOOD_CONDUCT_CERTIFICATE));
            add(requirements, "PROFESSIONAL", "Professional or business certificate", true,
                    Set.of(KycDocumentType.PROFESSIONAL_CERTIFICATE, KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE));
        }
        if (roles.stream().anyMatch(role -> Set.of(PMSRole.LANDLORD, PMSRole.TENANT, PMSRole.ESTATE_MANAGER,
                PMSRole.SALES_AGENT, PMSRole.SERVICE_PROVIDER, PMSRole.ASSET_PORTFOLIO_MANAGER,
                PMSRole.AFFILIATE).contains(role))) {
            add(requirements, "TAX", "KRA PIN certificate", true, Set.of(KycDocumentType.KRA_PIN_CERTIFICATE));
        }
        applyPublishedMarketplaceMatrix(requirements, roles, profileType);
        // Preserve a predictable customer journey: identity, selfie, organisation/role evidence, then tax.
        return Collections.unmodifiableSet(new LinkedHashSet<>(requirements.values()));
    }

    private void applyPublishedMarketplaceMatrix(Map<String,KycRequirement> target,Set<PMSRole> roles,ProfileType profileType){
        if(matrixReleases==null||matrixRequirements==null)return;
        var live=matrixReleases.findFirstByStatusAndActiveTrueOrderByVersionNoDesc("PUBLISHED").orElse(null);if(live==null)return;
        List<KycMatrixRequirement> rows=matrixRequirements.findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(live.getId());
        Set<String> providerKeys=new LinkedHashSet<>();if(roles.contains(PMSRole.SERVICE_PROVIDER)){providerKeys.add("SERVICE_PROVIDER");providerKeys.add("SOKO_MERCHANT");}
        for(KycMatrixRequirement row:rows){String commonKey=profileType==ProfileType.COMPANY?"BUSINESS":"PERSONAL";boolean common="COMMON".equals(row.getScopeType())&&commonKey.equals(row.getScopeKey());boolean provider="PROVIDER_TYPE".equals(row.getScopeType())&&providerKeys.contains(row.getScopeKey());if(!common&&!provider)continue;target.remove(row.getRequirementCode());if(!row.isActive())continue;Set<KycDocumentType> types=Arrays.stream(row.getAcceptedDocumentTypes().split(",")).map(String::trim).map(value->{try{return KycDocumentType.valueOf(value.toUpperCase(Locale.ROOT));}catch(Exception ignored){return null;}}).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));boolean required="MANDATORY".equals(row.getObligation())||("CONDITIONAL".equals(row.getObligation())&&conditionalForJourney(row,profileType));if(!types.isEmpty())add(target,row.getRequirementCode(),row.getRequirementLabel(),required,types);}
    }

    private boolean conditionalForJourney(KycMatrixRequirement row,ProfileType profileType){return switch(org.apache.commons.lang3.StringUtils.defaultString(row.getConditionRule())){case "NON_PASSPORT_IDENTITY"->true;case "PROFILE_IS"->profileType.name().equalsIgnoreCase(row.getConditionValue());default->false;};}

    private void add(Map<String, KycRequirement> target, String code, String label, boolean required,
                     Set<KycDocumentType> types) {
        target.putIfAbsent(code, new KycRequirement(code, label, required, types));
    }
}
