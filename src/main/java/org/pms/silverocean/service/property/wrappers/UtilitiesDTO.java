package org.pms.silverocean.service.property.wrappers;

public record UtilitiesDTO(long id, String name) {
    public UtilitiesDTO(long id) {
        this(id, null);
    }
}
