package org.pms.silverocean.service.property.wrappers;

import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record PropertyViewDTO(Long id,
                              ZonedDateTime createdOn,
                              LocalDateTime lastModifiedDate,
                              String name,
                              PMSPropertyType type,
                              PMSPropertyManagementMode managementMode,
                              String address,
                              String mapLocation,
                              String currency,
                              String ref,
                              String thumbnail,
                              boolean hasUnits) {
    public PropertyViewDTO(Property property, String thumbNail) {
        this(property.getId(), property.getCreatedOn(), property.getLastModifiedDate(), property.getName(),
                PMSPropertyType.valueOf(property.getType().toUpperCase()), property.getManagementMode(), property.getAddress(), property.getMapLocation(),
                property.getCurrency(), property.getRef(), thumbNail, property.isHasUnits());
    }
}
