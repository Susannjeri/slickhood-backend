package org.pms.silverocean.service.property;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.UnitTypeMappingRepo;
import org.pms.silverocean.database.pms.entities.UnitTypeToPropertyTypeMapping;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnitTypeDaoTest {
    @Test
    void replaceMappingsActivatesSelectionsAndDeactivatesRemovedTypes() {
        UnitTypeMappingRepo repository = mock(UnitTypeMappingRepo.class);
        UnitTypeToPropertyTypeMapping studio = mapping(PMSUnitTypes.STUDIO, true);
        UnitTypeToPropertyTypeMapping bedsitter = mapping(PMSUnitTypes.BEDSITTER, true);
        List<UnitTypeToPropertyTypeMapping> mappings = new ArrayList<>(List.of(studio, bedsitter));
        when(repository.findAllForUpdateByPropertyType(PMSPropertyType.APARTMENT_BLOCK)).thenReturn(mappings);

        AuditLogService audit = mock(AuditLogService.class);
        new UnitTypeDao(repository, audit).replaceMappings(PMSPropertyType.APARTMENT_BLOCK,
                Set.of(PMSUnitTypes.STUDIO, PMSUnitTypes.ONE_BEDROOM));

        assertThat(studio.isActive()).isTrue();
        assertThat(bedsitter.isActive()).isFalse();
        assertThat(mappings).anySatisfy(item -> {
            assertThat(item.getUnitType()).isEqualTo(PMSUnitTypes.ONE_BEDROOM);
            assertThat(item.isActive()).isTrue();
        });
        verify(repository).saveAll(anyList());
        verify(audit).createAuditLog(studio, "property_unit_type_catalog_update",
                "Enabled 2 unit types for APARTMENT_BLOCK", true);
    }

    @Test
    void replaceMappingsRefusesToLeaveAPropertyWithoutAnyUnitType() {
        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> new UnitTypeDao(mock(UnitTypeMappingRepo.class), mock(AuditLogService.class))
                        .replaceMappings(PMSPropertyType.APARTMENT_BLOCK, Set.of()));
        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.INVALID_FIELD_DATA);
    }

    private UnitTypeToPropertyTypeMapping mapping(PMSUnitTypes unitType, boolean active) {
        UnitTypeToPropertyTypeMapping mapping = new UnitTypeToPropertyTypeMapping(unitType, PMSPropertyType.APARTMENT_BLOCK);
        mapping.setActive(active);
        return mapping;
    }
}
