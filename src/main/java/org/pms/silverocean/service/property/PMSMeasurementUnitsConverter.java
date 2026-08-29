package org.pms.silverocean.service.property;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;
@Component
public class PMSMeasurementUnitsConverter implements Converter<String, PMSMeasurementUnits> {
    @Override
    public PMSMeasurementUnits convert(String id) {
        return Arrays.stream(PMSMeasurementUnits.values())
                .filter(t ->  id.equals(String.valueOf(t.getId())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid property type: " + id));
    }
}
