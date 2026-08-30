package org.pms.silverocean.service.kyc;

import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.users.ProfileType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class KycRequirementResolver {
    public Set<KycRequirement> resolve(Set<PMSRole> roles) {
        return resolve(roles, ProfileType.INDIVIDUAL);
    }

    public Set<KycRequirement> resolve(Set<PMSRole> roles, ProfileType profileType) {
        Map<String, KycRequirement> requirements = new LinkedHashMap<>();
        add(requirements, "IDENTITY_FRONT", "Government identity document", true,
                Set.of(KycDocumentType.NATIONAL_ID_FRONT, KycDocumentType.PASSPORT, KycDocumentType.ALIEN_ID_FRONT));
        add(requirements, "IDENTITY_BACK", "Back of identity document (not required for passports)", false,
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
            add(requirements, "GOOD_CONDUCT", "Certificate of good conduct", true,
                    Set.of(KycDocumentType.GOOD_CONDUCT_CERTIFICATE));
            add(requirements, "PROFESSIONAL", "Professional or business certificate", true,
                    Set.of(KycDocumentType.PROFESSIONAL_CERTIFICATE, KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE));
        }
        if (roles.stream().anyMatch(role -> Set.of(PMSRole.LANDLORD, PMSRole.ESTATE_MANAGER, PMSRole.SALES_AGENT,
                PMSRole.SERVICE_PROVIDER, PMSRole.ASSET_PORTFOLIO_MANAGER, PMSRole.AFFILIATE).contains(role))) {
            add(requirements, "TAX", "KRA PIN certificate", true, Set.of(KycDocumentType.KRA_PIN_CERTIFICATE));
        }
        return Set.copyOf(requirements.values());
    }

    private void add(Map<String, KycRequirement> target, String code, String label, boolean required,
                     Set<KycDocumentType> types) {
        target.putIfAbsent(code, new KycRequirement(code, label, required, types));
    }
}
