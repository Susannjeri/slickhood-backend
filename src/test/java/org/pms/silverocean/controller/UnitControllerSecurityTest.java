package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.property.PMSPropertyType;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitControllerSecurityTest {

    @Test
    void catalogReadAndWriteRemainSuperAdminOnly() throws Exception {
        assertEquals("hasRole('SUPER_ADMIN')",
                UnitController.class.getMethod("getUnitTypeCatalog")
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('SUPER_ADMIN')",
                UnitController.class.getMethod("updateUnitTypeCatalog", PMSPropertyType.class, java.util.Set.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
