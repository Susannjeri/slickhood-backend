package org.pms.silverocean.service.payment.wrappers;

public record AccountPropertyDefinition(
        String key,
        String labelKey,
        String descriptionKey,
        boolean encrypted,
        boolean displayField
) {}
