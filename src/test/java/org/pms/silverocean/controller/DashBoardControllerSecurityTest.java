package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class DashBoardControllerSecurityTest {
    @Test
    void dashboardTotalsRequireAuthenticationByDefault() {
        assertThat(DashBoardController.class.getAnnotation(PreAuthorize.class))
                .isNotNull()
                .extracting(PreAuthorize::value)
                .isEqualTo("isAuthenticated()");
    }
}
