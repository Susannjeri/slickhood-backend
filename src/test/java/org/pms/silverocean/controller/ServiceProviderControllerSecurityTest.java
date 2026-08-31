package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceProviderControllerSecurityTest {
    @Test void operationalControllerRequiresAuthenticationByDefault(){assertThat(ServiceProviderController.class.getAnnotation(PreAuthorize.class)).isNotNull().extracting(PreAuthorize::value).isEqualTo("isAuthenticated()");}
    @Test void financeEndpointCannotBecomeAnonymous() throws Exception {var method=ServiceProviderController.class.getMethod("updateBookingFinance",long.class,org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest.class);assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class)).isNotNull();assertThat(ServiceProviderController.class.getAnnotation(PreAuthorize.class)).isNotNull();}
}
