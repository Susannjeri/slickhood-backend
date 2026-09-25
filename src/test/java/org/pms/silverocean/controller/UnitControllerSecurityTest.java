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
                UnitController.class.getMethod("updateUnitTypeCatalog", String.class, java.util.Set.class,String.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void optimizedUnitOverviewAndGalleryRequireUnitViewPermission() throws Exception {
        String expected = "hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_UNIT)";
        assertEquals(expected,
                UnitController.class.getMethod("getUnitOverview", long.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals(expected,
                UnitController.class.getMethod("getUnitImages", long.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
