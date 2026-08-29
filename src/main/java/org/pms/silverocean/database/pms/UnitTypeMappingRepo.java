package org.pms.silverocean.database.pms;

import jakarta.persistence.LockModeType;
import org.pms.silverocean.database.pms.entities.UnitTypeToPropertyTypeMapping;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.pms.silverocean.service.property.PMSUnitTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface UnitTypeMappingRepo extends JpaRepository<UnitTypeToPropertyTypeMapping, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UnitTypeToPropertyTypeMapping> findUnitTypeToPropertyTypeMappingByUnitTypeAndPropertyType(PMSUnitTypes unitType, PMSPropertyType propertyType);

    @Query("SELECT u.unitType FROM UnitTypeToPropertyTypeMapping u WHERE u.propertyType=:propertyType")
    Set<PMSUnitTypes> findUnitTypeToPropertyTypeMappingByPropertyType(PMSPropertyType propertyType);
}
