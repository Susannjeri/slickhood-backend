package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.sales.CreateSaleRequest;
import org.pms.silverocean.service.sales.SaleMilestoneModels;
import org.pms.silverocean.service.sales.UpdateSaleRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SalesControllerSecurityTest {
    @Test
    void managerMutationsRequirePipelineManagementPermission() throws NoSuchMethodException {
        assertPermission("create", "MANAGE_SALE_PIPELINE", CreateSaleRequest.class);
        assertPermission("update", "MANAGE_SALE_PIPELINE", long.class, UpdateSaleRequest.class);
        assertPermission("milestone", "MANAGE_SALE_PIPELINE", long.class, SaleMilestoneModels.Create.class);
    }

    @Test
    void buyerAcceptanceUsesDedicatedPermission() throws NoSuchMethodException {
        assertPermission("accept", "ACCEPT_SALE_OFFER", long.class);
    }

    @Test
    void readsRequirePipelineVisibility() throws NoSuchMethodException {
        assertPermission("list", "VIEW_SALE_PIPELINE", Pageable.class);
        assertPermission("milestones", "VIEW_SALE_PIPELINE", long.class, Pageable.class);
    }

    private void assertPermission(String method, String permission, Class<?>... parameters) throws NoSuchMethodException {
        PreAuthorize boundary = SalesController.class.getDeclaredMethod(method, parameters).getAnnotation(PreAuthorize.class);
        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).contains(permission);
    }
}
