package org.pms.silverocean.service.property.wrappers;

public record TypeCatalogOption(
        String id,
        String name,
        String description,
        String category,
        int displayOrder,
        boolean common
) {}
