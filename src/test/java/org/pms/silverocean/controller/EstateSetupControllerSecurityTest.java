package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class EstateSetupControllerSecurityTest {
    @Test
    void setupStatusRequiresPropertyVisibilityPermission() throws NoSuchMethodException {
        PreAuthorize boundary = EstateSetupController.class.getDeclaredMethod("getStatus", long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).contains("VIEW_PROPERTY");
    }
}
