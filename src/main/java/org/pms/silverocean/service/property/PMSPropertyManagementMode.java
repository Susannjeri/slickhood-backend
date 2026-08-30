package org.pms.silverocean.service.property;

/**
 * The commercial workflow a property participates in. This is intentionally
 * separate from {@link PMSPropertyType}, which describes the physical asset.
 */
public enum PMSPropertyManagementMode {
    RENTAL,
    SALE,
    SERVICE_CHARGE
}
