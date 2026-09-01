package org.pms.silverocean.controller;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.reports.ReportModels;
import org.pms.silverocean.service.reports.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;

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
}
