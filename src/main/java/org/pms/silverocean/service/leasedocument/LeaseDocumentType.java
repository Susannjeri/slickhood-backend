package org.pms.silverocean.service.leasedocument;

public enum LeaseDocumentType {
    /** @deprecated Historical type retained so existing snapshots remain readable. */
    @Deprecated RENTAL_LETTER_OF_OFFER,
    RESIDENTIAL_LEASE_AGREEMENT, COMMERCIAL_LEASE_AGREEMENT, LATE_RENT_NOTICE,
    RENT_DEFAULT_CURE_NOTICE, LANDLORD_TERMINATION_NOTICE, TENANT_TERMINATION_NOTICE,
    /** @deprecated Historical type retained so existing snapshots remain readable. */
    @Deprecated ESTATE_AGREEMENT,
    ESTATE_RESIDENTIAL_AGREEMENT, PROPERTY_SALE_LETTER_OF_OFFER, PROPERTY_SALE_AGREEMENT;

    public boolean isTenantInitiated() { return this == TENANT_TERMINATION_NOTICE; }
    public boolean requiresLease() {
        return !isEstateDocument() && !isSaleDocument();
    }
    public boolean isEstateDocument() {
        return this == ESTATE_RESIDENTIAL_AGREEMENT || this == ESTATE_AGREEMENT;
    }
    public boolean isSaleDocument() {
        return this == PROPERTY_SALE_LETTER_OF_OFFER || this == PROPERTY_SALE_AGREEMENT;
    }
    public boolean isLegacy() { return this == RENTAL_LETTER_OF_OFFER || this == ESTATE_AGREEMENT; }
    public boolean isTenancyAgreement() {
        return this == RESIDENTIAL_LEASE_AGREEMENT || this == COMMERCIAL_LEASE_AGREEMENT;
    }
}
