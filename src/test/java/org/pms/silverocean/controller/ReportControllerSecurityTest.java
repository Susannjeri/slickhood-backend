package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.reports.ReportModels;
import org.pms.silverocean.service.reports.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportControllerSecurityTest {
    @Test
    void everyReportEndpointRequiresAuthentication() {
        PreAuthorize boundary = ReportController.class.getAnnotation(PreAuthorize.class);

        assertThat(boundary).isNotNull();
        assertThat(boundary.value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void exportPreventsCachingAndDisclosesItsSafetyLimit() {
        ReportService reports = mock(ReportService.class);
        I18NService i18n = mock(I18NService.class);
        when(reports.csv("INVOICE_COLLECTIONS", LocalDate.MIN, LocalDate.MAX))
                .thenReturn(new ReportModels.CsvExport("csv".getBytes(), true, 5_000));

        var response = new ReportController(reports, i18n)
                .export("INVOICE_COLLECTIONS", LocalDate.MIN, LocalDate.MAX);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("X-Report-Truncated")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("X-Report-Row-Limit")).isEqualTo("5000");
    }

    @Test
    void reportHttpContractIsASingletonListAndPrivateResponsesCannotBeCached() throws Exception {
        ReportService reports = mock(ReportService.class);
        I18NService i18n = mock(I18NService.class);
        var definition = new ReportModels.Definition("INVOICE_COLLECTIONS", "Invoice collections", "Collections",
                "FINANCE", true, "HISTORICAL", List.of("Landlord"));
        var data = new ReportModels.Data(definition, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                ZonedDateTime.now(), Map.of("Invoices", 0), List.of("Reference"), List.of(), false, 500);
        when(reports.catalog()).thenReturn(List.of(definition));
        when(reports.generate("INVOICE_COLLECTIONS", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15))).thenReturn(data);
        var mvc = MockMvcBuilders.standaloneSetup(new ReportController(reports, i18n)).build();
        mvc.perform(get("/reports/catalog"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("INVOICE_COLLECTIONS"))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"));
        mvc.perform(get("/reports/INVOICE_COLLECTIONS").param("from", "2026-09-01").param("to", "2026-09-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].definition.code").value("INVOICE_COLLECTIONS"))
                .andExpect(jsonPath("$.data[0].rows").isArray())
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"));
        mvc.perform(get("/reports/INVOICE_COLLECTIONS").param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }
}
