package org.pms.silverocean.service.property.wrappers;

import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;

public record DbUnitDTO(Long propertyId,
                        String ref,
                        PMSUnitTypes unitType,
                        PMSPropertyType propertyType,
                        Double size,
                        String leaseMode,
                        Double price,
                        String currency,
                        Boolean occupied,
                        boolean advertise,
                        String imagePath,
                        String thumbnail,
                        String utilities,
                        int measurementUnits,
                        Long unitId,
                        Long templateId) {
    public DbUnitDTO(Unit unit, String propertyType) {
        this(unit.getPropertyId(), unit.getRef(), PMSUnitTypes.valueOf(unit.getUnitType()), PMSPropertyType.valueOf(propertyType), unit.getSize(),
                 unit.getLeaseMode(), unit.getPrice(), unit.getCurrency(),
                unit.isOccupied(), unit.isAdvertise(), unit.getImagePath(), unit.getThumbnail(), unit.getUtilities(), unit.getMeasurementUnits(), unit.getId(), unit.getTemplateId());
    }

    public DbUnitDTO(Unit unit) {
        this(unit.getPropertyId(), unit.getRef(), PMSUnitTypes.valueOf(unit.getUnitType()), PMSPropertyType.valueOf(unit.getProperty().getType()), unit.getSize(),
                unit.getLeaseMode(), unit.getPrice(), unit.getCurrency(),
                unit.isOccupied(), unit.isAdvertise(), unit.getImagePath(), unit.getThumbnail(), unit.getUtilities(), unit.getMeasurementUnits(), unit.getId(), unit.getTemplateId());
    }
}
