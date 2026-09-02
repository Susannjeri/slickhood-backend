package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.insurance.InsuranceModels;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class InsuranceCatalogControllerSecurityTest {
    @Test
    void catalogueMutationsRemainPermissionScoped() throws Exception {
        assertThat(InsuranceController.class.getMethod("adminCompanies").getAnnotation(PreAuthorize.class).value())
                .contains("MANAGE_INSURANCE_CATALOG");
        assertThat(InsuranceController.class.getMethod("createCompany", InsuranceModels.CompanyCreateRequest.class)
                .getAnnotation(PreAuthorize.class).value()).contains("MANAGE_INSURANCE_CATALOG");
        assertThat(InsuranceController.class.getMethod("updateCompany", String.class, InsuranceModels.CompanyUpdateRequest.class)
                .getAnnotation(PreAuthorize.class).value()).contains("MANAGE_INSURANCE_CATALOG");
    }
}
