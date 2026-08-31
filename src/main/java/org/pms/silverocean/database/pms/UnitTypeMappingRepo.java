package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.UnitTypeToPropertyTypeMapping;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface UnitTypeMappingRepo extends JpaRepository<UnitTypeToPropertyTypeMapping, Long> {
    @Query("SELECT u.unitType FROM UnitTypeToPropertyTypeMapping u WHERE u.propertyType=:propertyType AND u.active=true")
    Set<PMSUnitTypes> findUnitTypeToPropertyTypeMappingByPropertyType(PMSPropertyType propertyType);

    List<UnitTypeToPropertyTypeMapping> findAllByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UnitTypeToPropertyTypeMapping u WHERE u.propertyType=:propertyType")
    List<UnitTypeToPropertyTypeMapping> findAllForUpdateByPropertyType(PMSPropertyType propertyType);
}
