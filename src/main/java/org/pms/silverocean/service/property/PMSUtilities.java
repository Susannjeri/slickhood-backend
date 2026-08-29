package org.pms.silverocean.service.property;

import lombok.Getter;

public enum PMSUtilities {

    WATER("property.utilities.water"), ELECTRICITY("property.utilities.electricity"),
    GAS("property.utilities.gas"), INTERNET("property.utilities.internet"),
    HEATING("property.utilities.heating"), COOLING("property.utilities.cooling");
    @Getter
    private final String name;
    PMSUtilities(String name) {
        this.name = name;
    }
}
