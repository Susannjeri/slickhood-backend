package org.pms.silverocean.service.kyc;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KycRequirementResolverTest {
    private final KycRequirementResolver resolver = new KycRequirementResolver();

    @Test void combinesRequirementsForEveryAssignedRoleWithoutDuplicates() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.LANDLORD, PMSRole.SERVICE_PROVIDER));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("OWNERSHIP")));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("GOOD_CONDUCT")));
        assertEquals(1, requirements.stream().filter(r -> r.code().equals("TAX")).count());
    }

    @Test void tenantStillReceivesIdentityAndSelfieRequirements() {
        Set<KycRequirement> requirements = resolver.resolve(Set.of(PMSRole.TENANT));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("IDENTITY_FRONT") && r.required()));
        assertTrue(requirements.stream().anyMatch(r -> r.code().equals("SELFIE") && r.required()));
    }
}
