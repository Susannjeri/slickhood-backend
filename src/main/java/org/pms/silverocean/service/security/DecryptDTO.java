package org.pms.silverocean.service.security;

public record DecryptDTO(boolean usedOldKey, String decryptedValue) {
}
