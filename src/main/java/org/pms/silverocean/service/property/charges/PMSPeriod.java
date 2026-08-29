package org.pms.silverocean.service.property.charges;

import lombok.Getter;

@Getter
public enum PMSPeriod {
    ONE_TIME("period.one.time.name"),
    MONTHLY("period.monthly.name"),
    ANNUAL("period.annual.name");

    private final String name;
    private PMSPeriod(String name) {
        this.name = name;
    }
}
