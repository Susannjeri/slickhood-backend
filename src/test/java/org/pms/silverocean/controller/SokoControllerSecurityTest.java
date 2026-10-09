package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.soko.SokoRequests;
import org.springframework.data.domain.Pageable;
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

    @Test
    void sellerDirectoryIsPublicButSavedDestinationsRequireAuthentication() throws Exception {
        var sellers=SokoController.class.getMethod("sellers",Pageable.class,String.class,String.class)
                .getAnnotation(PreAuthorize.class);
        var destinations=SokoController.class.getMethod("deliveryDestinations");
        var controller=SokoController.class.getAnnotation(PreAuthorize.class);

        assertThat(sellers).isNotNull();
        assertThat(sellers.value()).isEqualTo("permitAll()");
        assertThat(destinations.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(controller).isNotNull();
        assertThat(controller.value()).isEqualTo("isAuthenticated()");
    }
}
