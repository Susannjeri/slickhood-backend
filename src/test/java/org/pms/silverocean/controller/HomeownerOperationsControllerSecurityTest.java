package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HomeownerOperationsControllerSecurityTest {
    @Test
    void maintenanceControllerRequiresAuthenticationAtTheBoundary() {
        PreAuthorize annotation = MaintenanceController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("isAuthenticated()", annotation.value());
    }

    @Test
    void visitorReadAndCreateUseTheirMatchingCapabilities() throws Exception {
        assertEquals("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).REGISTER_VISITOR)",
                VisitorController.class.getMethod("registerVisitor", org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority(T(org.pms.silverocean.service.auth.roles.enums.Permission).VIEW_VISITOR_LIST)",
                VisitorController.class.getMethod("listMyVisitors", org.springframework.data.domain.Pageable.class, java.util.Optional.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
