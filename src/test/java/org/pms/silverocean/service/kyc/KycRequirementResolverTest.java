package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.users.ProfileType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KycRequirementResolverTest {
    private final KycRequirementResolver resolver = new KycRequirementResolver();

    @Test void combinesRequirementsForEveryAssignedRoleWithoutDuplicates() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.LANDLORD, PMSRole.SERVICE_PROVIDER));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("OWNERSHIP")));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("GOOD_CONDUCT")));
        assertFalse(requirements.stream().filter(r -> r.code().equals("GOOD_CONDUCT"))
                .findFirst().orElseThrow().required());
        assertEquals(1, requirements.stream().filter(r -> r.code().equals("TAX")).count());
    }

    @Test void tenantStillReceivesIdentityAndSelfieRequirements() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.TENANT));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("IDENTITY_FRONT") && r.required()));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("SELFIE") && r.required()));
    }

    @Test void organizationAccountRequiresLegalRegistrationEvidenceInAdditionToRepresentativeIdentity() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.ESTATE_MANAGER), ProfileType.COMPANY);
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("ORGANIZATION_REGISTRATION") && r.required()
                && r.acceptedTypes().contains(KycDocumentType.BUSINESS_REGISTRATION_CERTIFICATE)));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("IDENTITY_FRONT") && r.required()));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("SELFIE") && r.required()));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("TAX") && r.required()));
    }

    @Test void individualAccountDoesNotReceiveOrganizationRegistrationRequirement() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.LANDLORD), ProfileType.INDIVIDUAL);
        assertTrue(requirements.stream().noneMatch(r -> r.code().equals("ORGANIZATION_REGISTRATION")));
    }
}
