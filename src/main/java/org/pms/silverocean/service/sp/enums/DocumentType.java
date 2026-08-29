package org.pms.silverocean.service.sp.enums;

import lombok.Getter;

@Getter
public enum DocumentType {
    NATIONAL_ID("service.provider.doc.type.national_id"),
    PASSPORT("service.provider.doc.type.passport"),
    BUSINESS_REGISTRATION("service.provider.doc.type.business_registration"),
    TAX_CERTIFICATE("service.provider.doc.type.tax_certificate"),
    PROFESSIONAL_CERTIFICATE("service.provider.doc.type.professional_certificate"),
    INSURANCE_CERTIFICATE("service.provider.doc.type.insurance_certificate"),
    GOOD_CONDUCT("service.provider.doc.type.good_conduct"),
    WORK_PERMIT("service.provider.doc.type.work_permit"),
    ACADEMIC_CERTIFICATE("service.provider.doc.type.academic_certificate"),
    HEALTH_CERTIFICATE("service.provider.doc.type.health_certificate"),
    OTHER("service.provider.doc.type.other");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }
}
