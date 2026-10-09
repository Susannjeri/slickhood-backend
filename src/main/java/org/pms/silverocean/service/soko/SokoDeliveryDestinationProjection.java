package org.pms.silverocean.service.soko;

/**
 * Internal projection used to build customer-owned Soko delivery destinations.
 * It is never returned directly because legacy map locations still need strict parsing.
 */
public interface SokoDeliveryDestinationProjection {
    long getUnitId();
    long getPropertyId();
    String getUnitRef();
    String getPropertyName();
    String getAddress();
    String getMapLocation();
}
