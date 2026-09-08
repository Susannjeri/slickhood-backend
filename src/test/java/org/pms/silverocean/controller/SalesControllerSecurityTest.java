package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.sales.CreateSaleRequest;
import org.pms.silverocean.service.sales.EscrowInvoiceModels;
import org.pms.silverocean.service.sales.SaleMilestoneModels;
import org.pms.silverocean.service.sales.UpdateSaleRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class SalesControllerSecurityTest {
    @Test void buyerAcceptanceResponseNeverLeaksInternalNotes() throws Exception {
        var service = org.mockito.Mockito.mock(org.pms.silverocean.service.sales.SalesService.class);
        var i18n = org.mockito.Mockito.mock(org.pms.silverocean.service.I18NService.class);
        var sale = new org.pms.silverocean.database.pms.entities.SaleTransaction();
        sale.setId(1L); sale.setStatus(org.pms.silverocean.service.sales.SaleStatus.RESERVED);
        sale.setNotes("PRIVATE SELLER NEGOTIATION"); sale.setCurrency("KES");
        org.mockito.Mockito.when(service.acceptOffer(1L)).thenReturn(sale);
        var response = new SalesController(service,i18n).accept(1L);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response.getBody());
        assertThat(json).contains("RESERVED").doesNotContain("PRIVATE SELLER NEGOTIATION").doesNotContain("notes");
        assertThat(sale.getNotes()).isEqualTo("PRIVATE SELLER NEGOTIATION");
    }

    @Test
    void managerMutationsRequirePipelineManagementPermission() throws NoSuchMethodException {
        assertPermission("create", "MANAGE_SALE_PIPELINE", CreateSaleRequest.class);
        assertPermission("update", "MANAGE_SALE_PIPELINE", long.class, UpdateSaleRequest.class);
        assertPermission("milestone", "MANAGE_SALE_PIPELINE", long.class, SaleMilestoneModels.Create.class);
        assertPermission("escrowInvoice", "MANAGE_SALE_PIPELINE", long.class, EscrowInvoiceModels.Create.class);
    }

    @Test
    void buyerAcceptanceUsesDedicatedPermission() throws NoSuchMethodException {
        assertPermission("accept", "ACCEPT_SALE_OFFER", long.class);
    }

    @Test
    void readsRequirePipelineVisibility() throws NoSuchMethodException {
        assertPermission("list", "VIEW_SALE_PIPELINE", Pageable.class, String.class);
        assertPermission("milestones", "VIEW_SALE_PIPELINE", long.class, Pageable.class);
    }

    private void assertPermission(String method, String permission, Class<?>... parameters) throws NoSuchMethodException {
        PreAuthorize boundary = SalesController.class.getDeclaredMethod(method, parameters).getAnnotation(PreAuthorize.class);
        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).contains(permission);
    }
}
