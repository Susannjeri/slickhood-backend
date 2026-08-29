package org.pms.silverocean.service.property;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.UnitTypeMappingRepo;
import org.pms.silverocean.database.pms.entities.UnitTypeToPropertyTypeMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Service @Slf4j
public class UnitTypeDao {
    private final UnitTypeMappingRepo unitTypeMappingRepo;
    private Set<UnitTypeToPropertyTypeMapping> unitMapping;

    private final LoadingCache<PMSPropertyType, Set<PMSUnitTypes>> unitTypeCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(24)).build(new CacheLoader<>() {
                @Override
                public Set<PMSUnitTypes> load(PMSPropertyType propertyType) {
                    try {
                        return unitTypeMappingRepo.findUnitTypeToPropertyTypeMappingByPropertyType(propertyType);
                    } catch (Exception e) {
                        return Set.of();
                    }
                }
            });

    public UnitTypeDao(UnitTypeMappingRepo unitTypeMappingRepo) {
        this.unitTypeMappingRepo = unitTypeMappingRepo;
    }

    public Set<PMSUnitTypes> getByPropertyType(PMSPropertyType propertyType) {
        try {
            return unitTypeCache.get(propertyType);
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
            return Set.of();
        }
    }

    @Transactional
    public void initUnitTypes() {
        unitMapping = new HashSet<>();
        log.info("Checking unit types in database...");
        // 1. Residential Mappings
        map(PMSPropertyType.APARTMENT_BLOCK,
                PMSUnitTypes.BEDSITTER, PMSUnitTypes.STUDIO, PMSUnitTypes.ONE_BEDROOM,
                PMSUnitTypes.TWO_BEDROOM, PMSUnitTypes.THREE_BEDROOM, PMSUnitTypes.PENTHOUSE);
        map(PMSPropertyType.STANDALONE_HOUSE,
                PMSUnitTypes.TWO_BEDROOM, PMSUnitTypes.THREE_BEDROOM,
                PMSUnitTypes.FOUR_BEDROOM, PMSUnitTypes.FIVE_BEDROOM_PLUS);
        map(PMSPropertyType.MAISONETTE, PMSUnitTypes.THREE_BEDROOM, PMSUnitTypes.FOUR_BEDROOM);
        map(PMSPropertyType.TOWNHOUSE, PMSUnitTypes.THREE_BEDROOM, PMSUnitTypes.FOUR_BEDROOM);
        map(PMSPropertyType.BUNGALOW, PMSUnitTypes.TWO_BEDROOM, PMSUnitTypes.THREE_BEDROOM);
        map(PMSPropertyType.STUDENT_HOSTEL,
                PMSUnitTypes.SINGLE_ROOM, PMSUnitTypes.SHARED_ROOM, PMSUnitTypes.ENSUITE_ROOM);
        map(PMSPropertyType.SERVICED_APARTMENT,
                PMSUnitTypes.STUDIO, PMSUnitTypes.ONE_BEDROOM, PMSUnitTypes.TWO_BEDROOM);
        map(PMSPropertyType.GATED_ESTATE, PMSUnitTypes.VILLAS, PMSUnitTypes.TOWNHOUSES);

        // 2. Commercial Mappings
        map(PMSPropertyType.OFFICE_BLOCK_CBD,
                PMSUnitTypes.OPEN_PLAN, PMSUnitTypes.PARTITIONED_OFFICE, PMSUnitTypes.EXECUTIVE_SUITE);
        map(PMSPropertyType.GRADE_A_OFFICE, PMSUnitTypes.WHOLE_FLOOR, PMSUnitTypes.HALF_FLOOR);
        map(PMSPropertyType.RETAIL_SHOP, PMSUnitTypes.SHOP, PMSUnitTypes.KIOSK);
        map(PMSPropertyType.SHOPPING_MALL, PMSUnitTypes.ANCHOR_TENANT, PMSUnitTypes.INLINE_SHOP);
        map(PMSPropertyType.MIXED_RETAIL_PLAZA, PMSUnitTypes.SHOP, PMSUnitTypes.MINI_SUPERMARKET);
        map(PMSPropertyType.BUSINESS_PARK, PMSUnitTypes.OFFICE_UNIT);
        map(PMSPropertyType.MEDICAL_PLAZA, PMSUnitTypes.CLINIC_ROOM, PMSUnitTypes.LAB_SPACE);
        map(PMSPropertyType.CO_WORKING_SPACE, PMSUnitTypes.DESK, PMSUnitTypes.PRIVATE_OFFICE);

        // 3. Mixed Use Mappings
        map(PMSPropertyType.SHOPS_AND_APARTMENTS,
                PMSUnitTypes.SHOP, PMSUnitTypes.ONE_BEDROOM, PMSUnitTypes.TWO_BEDROOM);
        map(PMSPropertyType.OFFICE_AND_RESIDENTIAL, PMSUnitTypes.OFFICE_UNIT, PMSUnitTypes.ONE_BEDROOM);
        map(PMSPropertyType.COMMERCIAL_AND_HOSTELS, PMSUnitTypes.SHOP, PMSUnitTypes.STUDENT_ROOM);
        map(PMSPropertyType.MALL_AND_APARTMENTS, PMSUnitTypes.RETAIL_UNIT, PMSUnitTypes.APARTMENT_UNIT);

        // 4. Industrial Mappings
        map(PMSPropertyType.WAREHOUSE, PMSUnitTypes.OPEN_WAREHOUSE, PMSUnitTypes.RACKED_WAREHOUSE);
        map(PMSPropertyType.GODOWN, PMSUnitTypes.STORAGE_UNIT);
        map(PMSPropertyType.FACTORY_LIGHT, PMSUnitTypes.MANUFACTURING_UNIT);
        map(PMSPropertyType.FACTORY_HEAVY, PMSUnitTypes.MANUFACTURING_UNIT);
        map(PMSPropertyType.EPZ_FACILITY, PMSUnitTypes.PRODUCTION_UNIT);
        map(PMSPropertyType.COLD_STORAGE, PMSUnitTypes.TEMPERATURE_CONTROLLED_UNIT);

        // 5. Hospitality Mappings
        map(PMSPropertyType.HOTEL, PMSUnitTypes.STANDARD_ROOM, PMSUnitTypes.DELUXE_ROOM, PMSUnitTypes.SUITE);
        map(PMSPropertyType.BOUTIQUE_HOTEL, PMSUnitTypes.STANDARD_ROOM, PMSUnitTypes.SUITE);
        map(PMSPropertyType.RESORT, PMSUnitTypes.ROOM, PMSUnitTypes.COTTAGE, PMSUnitTypes.VILLAS);
        map(PMSPropertyType.AIRBNB_UNIT, PMSUnitTypes.ENTIRE_UNIT, PMSUnitTypes.SINGLE_ROOM);
        map(PMSPropertyType.GUEST_HOUSE, PMSUnitTypes.STANDARD_ROOM);
        if (!CollectionUtils.isEmpty(unitMapping)) {
            unitTypeMappingRepo.saveAll(unitMapping);
            log.info("Successfully persisted {} new unit mappings.", unitMapping.size());
            unitMapping = null;
        }
    }


    private void map(PMSPropertyType property, PMSUnitTypes... units) {
        for (PMSUnitTypes unit : units) {
            if (unitTypeMappingRepo.findUnitTypeToPropertyTypeMappingByUnitTypeAndPropertyType(unit, property).isEmpty()) {
                unitMapping.add(new UnitTypeToPropertyTypeMapping(unit, property));
            }
        }
    }
}
