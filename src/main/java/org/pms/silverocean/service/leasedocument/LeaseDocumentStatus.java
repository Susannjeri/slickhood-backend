package org.pms.silverocean.service.leasedocument;

public enum LeaseDocumentStatus {
    DRAFT, ISSUED, ACKNOWLEDGED, PARTIALLY_SIGNED, SIGNED, CANCELLED, EXPIRED;

    /** Expired offers remain readable; do not wait for another draft to update their display status. */
    public static LeaseDocumentStatus displayed(org.pms.silverocean.database.pms.entities.LeaseDocument document) {
        if (document.getDocumentType() == LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER
                && java.util.EnumSet.of(DRAFT, ISSUED, ACKNOWLEDGED, PARTIALLY_SIGNED).contains(document.getStatus())
                && document.getResponseDueDate() != null
                && document.getResponseDueDate().isBefore(java.time.LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId()))) {
            return EXPIRED;
        }
        return document.getStatus();
    }
}
