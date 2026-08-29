package org.pms.silverocean.controller.wrappers;

public record VerificationOptionsDTO(boolean email, boolean phone, boolean google, String preferred) {
}
