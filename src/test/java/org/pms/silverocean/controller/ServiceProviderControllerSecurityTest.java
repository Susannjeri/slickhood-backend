package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceProviderControllerSecurityTest {
    @Test void operationalControllerRequiresAuthenticationByDefault(){assertThat(ServiceProviderController.class.getAnnotation(PreAuthorize.class)).isNotNull().extracting(PreAuthorize::value).isEqualTo("isAuthenticated()");}
    @Test void financeEndpointRequiresFinanceRole() throws Exception {var method=ServiceProviderController.class.getMethod("updateBookingFinance",long.class,org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest.class);assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class)).isNotNull();assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('FINANCE')");}
    @Test void publicDirectoryOverridesAuthenticatedControllerDefault() {
        var methods = java.util.Arrays.stream(ServiceProviderController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("directory") || method.getName().equals("directoryDetails"))
                .toList();
        assertThat(methods).hasSize(2).allSatisfy(method ->
                assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull()
                        .extracting(PreAuthorize::value).isEqualTo("permitAll()"));
    }
    @Test void ownerPublishEndpointUsesEditServicePermission() throws Exception {
        var method = ServiceProviderController.class.getMethod("publishService", long.class);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class).value())
                .containsExactly("/service/{serviceId}/publish");
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains("EDIT_SP_SERVICE");
    }
}
