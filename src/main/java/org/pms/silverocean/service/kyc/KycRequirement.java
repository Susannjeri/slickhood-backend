package org.pms.silverocean.service.kyc;

import java.util.Set;

public record KycRequirement(String code, String label, boolean required, Set<KycDocumentType> acceptedTypes) { }
