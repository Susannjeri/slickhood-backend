package org.pms.silverocean.service.property;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum PMSMeasurementUnits {
    SQUARE_FEET(1, "property.measurement.unit.square.feet.name"),
    SQUARE_METERS(2, "property.measurement.unit.square.meter.name"),
    ACRES(3, "property.measurement.unit.acres.name"),
    HECTARES(4, "property.measurement.unit.hectares.name"),
    SQUARE_YARDS(5, "property.measurement.unit.square.yards.name"),

    // Length
    FEET(6, "property.measurement.unit.feet.name"),
    METERS(7, "property.measurement.unit.meters.name"),

    // Volume
    CUBIC_FEET(8, "property.measurement.unit.cubic.feet.name"),
    CUBIC_METERS(9, "property.measurement.unit.cubic.meters.name");

    private final int id;
    private final String name;

    @JsonCreator


    PMSMeasurementUnits(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }
}
