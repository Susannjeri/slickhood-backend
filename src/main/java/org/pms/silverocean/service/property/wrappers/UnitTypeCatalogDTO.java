package org.pms.silverocean.service.property.wrappers;

import java.util.List;

public record UnitTypeCatalogDTO(
        List<PropertyUnitTypeCatalogDTO> propertyTypes,
        List<TypeCatalogOption> availableUnitTypes
) {}
