package org.pms.silverocean.service.property.wrappers;

import java.util.Set;

public record PropertyUnitTypeCatalogDTO(
        TypeCatalogOption propertyType,
        Set<String> enabledUnitTypeIds
) {}
