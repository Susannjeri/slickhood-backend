package org.pms.silverocean.service.kyc;

import java.util.List;
import java.util.Set;

public record KycCaseView(Long id, String status, String accountStatus, String consentVersion, String reviewNotes,
                          boolean phoneVerified, String verifiedPhoneNumber, String phoneVerifiedAt, String registryStatus,
                          boolean ocrEnabled, Set<KycRequirement> requirements,
                          Set<String> missingRequirementCodes, List<KycDocumentView> documents) { }
