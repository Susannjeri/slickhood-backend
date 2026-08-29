package org.pms.silverocean.service.property.charges;

import lombok.Getter;

@Getter
public enum PMSChargeTypes {
    SERVICE("charge.service.name"),
    WATER("charge.water.name"),
    ELECTRICITY("charge.electricity.name"),
    GARBAGE("charge.garbage.name"),
    DEPOSIT("charge.deposit.name"),
    SECURITY("charge.security.name"),
    LATE_FEES("charge.late.fees.name"),
    PARKING("charge.parking.name");

    private final String name;

    PMSChargeTypes(String name) {
        this.name = name;
    }
}
