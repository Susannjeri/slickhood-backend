package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PublicInsuranceControllerSecurityTest {
    @Test void onlyDedicatedGuestControllerIsPublic() throws Exception {
        assertThat(PublicInsuranceController.class.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(InsuranceController.class.getMethod("companies").getAnnotation(PreAuthorize.class).value())
                .isEqualTo("isAuthenticated()");
        assertThat(InsuranceController.class.getMethod("createCase", org.pms.silverocean.service.insurance.InsuranceModels.CaseRequest.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }
}
