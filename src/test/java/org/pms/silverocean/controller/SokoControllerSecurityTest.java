package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.soko.SokoRequests;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SokoControllerSecurityTest {
    @Test
    void financeEndpointRequiresFinanceOrSuperAdmin() throws Exception {
        var method = SokoController.class.getMethod("finance", long.class, SokoRequests.FinanceUpdate.class);
        var authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("FINANCE", "SUPER_ADMIN");
    }
}
