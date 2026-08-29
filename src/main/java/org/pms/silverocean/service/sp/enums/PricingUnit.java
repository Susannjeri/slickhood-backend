package org.pms.silverocean.service.sp.enums;

import lombok.Getter;

@Getter
public enum PricingUnit {
    PER_HOUR("service.provider.pricing.unit.per.hour"),
    PER_DAY("service.provider.pricing.unit.per.day"),
    PER_WEEK("service.provider.pricing.unit.per.week"),
    PER_MONTH("service.provider.pricing.unit.per.month"),
    PER_VISIT("service.provider.pricing.unit.per.visit"),
    PER_JOB("service.provider.pricing.unit.per.job"),
    PER_SQFT("service.provider.pricing.unit.per.sqft"),
    PER_SQMT("service.provider.pricing.unit.per.sqmt"),
    PER_ITEM("service.provider.pricing.unit.per.item"),
    PER_KM("service.provider.pricing.unit.per.km"),
    FIXED("service.provider.pricing.unit.fixed");

    private final String label;

    PricingUnit(String label) {
        this.label = label;
    }
}
