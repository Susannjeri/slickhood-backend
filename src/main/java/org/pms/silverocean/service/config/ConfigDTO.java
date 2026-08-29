package org.pms.silverocean.service.config;

public record ConfigDTO(long id, String name, String stringValue, int intValue, boolean encrypted) {
}
