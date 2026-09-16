package org.pms.silverocean.service.property.wrappers;

import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;

public record DbUnitDTO(Long propertyId,
                        String ref,
                        PMSUnitTypes unitType,
                        String propertyType,
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
                        Long templateId,
                        org.pms.silverocean.service.property.PMSPropertyCategory propertyCategory) {
    public DbUnitDTO(Long propertyId,String ref,PMSUnitTypes unitType,PMSPropertyType propertyType,Double size,String leaseMode,Double price,String currency,Boolean occupied,boolean advertise,String imagePath,String thumbnail,String utilities,int measurementUnits,Long unitId,Long templateId){this(propertyId,ref,unitType,propertyType.name(),size,leaseMode,price,currency,occupied,advertise,imagePath,thumbnail,utilities,measurementUnits,unitId,templateId,propertyType.getCategory());}

    public DbUnitDTO(Unit unit,String propertyType){this(unit,propertyType,category(propertyType,unit));}
    public DbUnitDTO(Unit unit, String propertyType,org.pms.silverocean.service.property.PMSPropertyCategory propertyCategory) {
        this(unit.getPropertyId(), unit.getRef(), PMSUnitTypes.valueOf(unit.getUnitType()), propertyType, unit.getSize(),
                 unit.getLeaseMode(), unit.getPrice(), unit.getCurrency(),
                unit.isOccupied(), unit.isAdvertise(), unit.getImagePath(), unit.getThumbnail(), unit.getUtilities(), unit.getMeasurementUnits(), unit.getId(), unit.getTemplateId(), propertyCategory==null?category(propertyType,unit):propertyCategory);
    }

    public DbUnitDTO(Unit unit) {
        this(unit.getPropertyId(), unit.getRef(), PMSUnitTypes.valueOf(unit.getUnitType()), unit.getProperty().getType(), unit.getSize(),
                unit.getLeaseMode(), unit.getPrice(), unit.getCurrency(),
                unit.isOccupied(), unit.isAdvertise(), unit.getImagePath(), unit.getThumbnail(), unit.getUtilities(), unit.getMeasurementUnits(), unit.getId(), unit.getTemplateId(), category(unit.getProperty().getType(),unit));
    }
    private static org.pms.silverocean.service.property.PMSPropertyCategory category(String code,Unit unit){try{return PMSPropertyType.valueOf(code).getCategory();}catch(IllegalArgumentException error){return unit.getProperty().getTypeCategoryResolved();}}
}
