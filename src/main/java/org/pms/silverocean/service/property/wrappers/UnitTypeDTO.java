package org.pms.silverocean.service.property.wrappers;

public record UnitTypeDTO(long id, String name) {
    public UnitTypeDTO(long id) {
        this(id, null);
    }
}
