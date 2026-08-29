package org.pms.silverocean.service.leasedocument;

public enum LeaseDocumentType {
    RESIDENTIAL_LEASE_AGREEMENT, COMMERCIAL_LEASE_AGREEMENT, LATE_RENT_NOTICE,
    RENT_DEFAULT_CURE_NOTICE, LANDLORD_TERMINATION_NOTICE, TENANT_TERMINATION_NOTICE,
    ESTATE_AGREEMENT, PROPERTY_SALE_AGREEMENT;

    public boolean isTenantInitiated() { return this == TENANT_TERMINATION_NOTICE; }
    public boolean requiresLease() { return this != ESTATE_AGREEMENT && this != PROPERTY_SALE_AGREEMENT; }
}
