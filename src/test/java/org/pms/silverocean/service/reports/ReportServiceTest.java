package org.pms.silverocean.service.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock UserDao users;
    @Mock PMSInvoiceRepo invoices;
    @Mock PMSPaymentRepo payments;
    @Mock UnitRepo units;
    @Mock VisitorRepo visitors;
    @Mock SaleTransactionRepo sales;
    @Mock EstateServiceChargeRepo estateCharges;
    @Mock ServiceBookingRepo serviceBookings;
    @Mock SokoOrderRepo sokoOrders;
    @Mock FinancialLedgerLineRepo ledgerLines;
    @Mock LeaseRepo leases;
    @Mock UserSubscriptionRepo subscriptions;
    @Mock AffiliateCommissionRepo affiliateCommissions;
    @Mock KycCaseRepo kycCases;
    @Mock NotificationRepo notifications;
    @Mock GateDeviceRepo gateDevices;
    @Mock MaintenanceWorkOrderRepo maintenanceOrders;
    @Mock FinancialReportScopeService financialScope;
    @InjectMocks ReportService service;

    @BeforeEach
    void user() {
        lenient().when(financialScope.resolve()).thenReturn(FinancialReportScopeService.Scope.personal());
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);
        lenient().when(users.getUserId()).thenReturn(41L);
    }

    @Test
    void catalogOnlyContainsReportsAvailableToActiveRole() {
        var catalog = service.catalog();
        assertFalse(catalog.isEmpty());
        assertTrue(catalog.stream().allMatch(report -> report.availableToRoles().contains(PMSRole.LANDLORD.getName())));
        assertTrue(catalog.stream().anyMatch(report -> report.code().equals("INVOICE_COLLECTIONS")));
        assertTrue(catalog.stream().anyMatch(report -> report.code().equals("ACCOUNT_STATEMENT")));
        assertTrue(catalog.stream().anyMatch(report -> report.code().equals("LEASE_EXPIRY")));
        assertTrue(catalog.stream().noneMatch(report -> report.code().equals("KYC_OPERATIONS")));
    }

    @Test
    void invoiceReportCalculatesCollectionsWithoutExposingCustomerContacts() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setRef("INV-100");
        invoice.setAmount(10_000);
        invoice.setPendingAmount(2_500);
        invoice.setCurrency("KES");
        invoice.setPropertyId(7);
        invoice.setUnitId(12);
        invoice.setCustomerEmail("private@example.com");
        invoice.setCustomerPhoneNumber("+254700000000");
        invoice.setBillingType("RENTAL");
        invoice.setDueDate(LocalDate.now().minusDays(2));
        invoice.setActive(true);
        when(invoices.findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), any(Pageable.class))).thenReturn(List.of(invoice));

        var report = service.generate("invoice_collections", LocalDate.now().minusDays(10), LocalDate.now());

        assertEquals(1, report.metrics().get("Invoices"));
        assertEquals("OVERDUE", report.rows().getFirst().get("Status"));
        assertFalse(report.columns().contains("Customer email"));
        assertFalse(report.rows().getFirst().toString().contains("private@example.com"));
    }

    @Test
    void rejectsRangesLongerThanOneYear() {
        assertThrows(PMSCustomException.class,
                () -> service.generate("INVOICE_COLLECTIONS", LocalDate.now().minusDays(400), LocalDate.now()));
    }

    @Test
    void interactiveReportCapsRowsAndSignalsTruncation() {
        PMSInvoice invoice = invoice("INV-ROW");
        when(invoices.findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.nCopies(501, invoice));

        var report = service.generate("INVOICE_COLLECTIONS", LocalDate.now().minusDays(10), LocalDate.now());

        assertEquals(500, report.rows().size());
        assertEquals(500, report.rowLimit());
        assertTrue(report.truncated());
        verify(invoices).findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), argThat(page -> page.getPageSize() == 501));
    }

    @Test
    void csvExportUsesLargerBoundAndNeutralizesWhitespacePrefixedFormula() {
        PMSInvoice invoice = invoice(" \t=HYPERLINK(\"https://example.invalid\")");
        when(invoices.findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), any(Pageable.class))).thenReturn(List.of(invoice));

        var export = service.csv("INVOICE_COLLECTIONS", LocalDate.now().minusDays(10), LocalDate.now());
        String csv = new String(export.content(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"' \t=HYPERLINK(\"\"https://example.invalid\"\")\""));
        assertEquals(5_000, export.rowLimit());
        assertFalse(export.truncated());
        verify(invoices).findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), argThat(page -> page.getPageSize() == 5_001));
    }

    @Test
    void leaseExpiryDefaultsToAForwardNinetyDayWindow() {
        when(leases.findExpiringForReport(eq(41L), eq(false), any(), any(), any(Pageable.class))).thenReturn(List.of());
        LocalDate today = LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId());

        var report = service.generate("LEASE_EXPIRY", null, null);

        assertEquals("FORWARD", report.definition().dateMode());
        assertEquals(today, report.from());
        assertEquals(today.plusDays(90), report.to());
        verify(leases).findExpiringForReport(eq(41L), eq(false), eq(today), eq(today.plusDays(90)),
                argThat(page -> page.getPageSize() == 501));
    }

    @Test
    void historicalReportsRejectFutureDates() {
        LocalDate today = LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId());
        assertThrows(PMSCustomException.class,
                () -> service.generate("INVOICE_COLLECTIONS", today, today.plusDays(1)));
    }

    @Test
    void everyCatalogReportGeneratesAndExportsWithStableHeadersWhenEmpty() {
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        var catalog = service.catalog();
        assertEquals(16, catalog.size());
        for (var definition : catalog) {
            var report = service.generate(definition.code(), null, null);
            assertEquals(definition.code(), report.definition().code());
            assertTrue(report.rows().isEmpty());
            assertFalse(report.columns().isEmpty(), definition.code());
            assertFalse(report.truncated());
            var csv = service.csv(definition.code(), null, null);
            String content = new String(csv.content(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(content.contains("\"" + report.columns().getFirst() + "\""), definition.code());
            assertTrue(content.endsWith("\r\n"));
        }
    }

    @Test
    void rejectsUnknownReportsAndReportsOutsideActiveRole() {
        assertThrows(PMSCustomException.class, () -> service.generate("UNKNOWN", null, null));
        assertThrows(PMSCustomException.class, () -> service.generate("KYC_OPERATIONS", null, null));
        assertThrows(PMSCustomException.class, () -> service.csv("NOTIFICATION_DELIVERY", null, null));
        org.mockito.Mockito.verifyNoInteractions(kycCases, notifications);
    }

    @Test
    void forwardReportsCannotBeUsedToQueryAnUnboundedFutureWindow() {
        LocalDate today = LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId());
        assertThrows(PMSCustomException.class,
                () -> service.generate("LEASE_EXPIRY", today.plusYears(10), today.plusYears(10).plusDays(1)));
        org.mockito.Mockito.verifyNoInteractions(leases);
    }

    @Test
    void snapshotIgnoresHistoricalFiltersAndUsesTheCurrentDate() {
        LocalDate today = LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId());
        var report = service.generate("OCCUPANCY_RENT_ROLL", today.minusYears(2), today.minusYears(1));
        assertEquals(today, report.from());
        assertEquals(today, report.to());
        assertFalse(report.definition().supportsDateRange());
    }

    @Test
    void csvExportRetainsALargerHardLimitAndSignalsIncompleteTotals() {
        when(invoices.findForScopedReport(eq(41L), eq(false), eq(false), eq(List.of(-1L)), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.nCopies(5_001, invoice("INV-CAPPED")));
        var csv = service.csv("INVOICE_COLLECTIONS", null, null);
        assertTrue(csv.truncated());
        assertEquals(5_000, csv.rowLimit());
        String content = new String(csv.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(5_001, content.lines().count());
    }

    @Test
    void screenAndExportUseExactlyTheSameWorkspaceRestrictionForAllFinancialQueries() {
        when(financialScope.resolve()).thenReturn(new FinancialReportScopeService.Scope(true,List.of(7L),-9L,"PROPERTY_ACCOUNTANT"));
        for (String code : List.of("INVOICE_COLLECTIONS","PAYMENT_RECONCILIATION","ACCOUNT_STATEMENT","SALES_PIPELINE","ESTATE_CHARGES")) {
            service.generate(code,null,null);
            service.csv(code,null,null);
        }
        verify(invoices, org.mockito.Mockito.times(2)).findForScopedReport(eq(41L),eq(false),eq(true),eq(List.of(7L)),any(),any(),any());
        verify(payments, org.mockito.Mockito.times(2)).findForScopedReport(eq(41L),eq(false),eq(true),eq(List.of(7L)),any(),any(),any());
        verify(ledgerLines, org.mockito.Mockito.times(2)).findForScopedStatement(eq(41L),eq(false),eq(true),eq(List.of(7L)),eq(-9L),eq("PROPERTY_ACCOUNTANT"),any(),any(),any());
        verify(sales, org.mockito.Mockito.times(2)).findForScopedReport(eq(41L),eq(false),eq(true),eq(List.of(7L)),eq(-9L),eq("PROPERTY_ACCOUNTANT"),any(),any(),any());
        verify(estateCharges, org.mockito.Mockito.times(2)).findForScopedReport(eq(41L),eq(false),eq(true),eq(List.of(7L)),eq(-9L),eq("PROPERTY_ACCOUNTANT"),any(),any(),any());
        verify(invoices,org.mockito.Mockito.never()).findForReport(anyLong(),anyBoolean(),any(),any(),any());
        verify(ledgerLines,org.mockito.Mockito.never()).findForStatement(anyLong(),anyBoolean(),any(),any(),any());
    }

    @Test
    void invalidWorkspaceStopsBothReportAndExportBeforeAnyFinancialQuery() {
        when(financialScope.resolve()).thenThrow(new PMSCustomException(org.pms.silverocean.common.ResponseCode.WORKSPACE_SELECTION_INVALID));
        assertThrows(PMSCustomException.class,()->service.generate("ACCOUNT_STATEMENT",null,null));
        assertThrows(PMSCustomException.class,()->service.csv("INVOICE_COLLECTIONS",null,null));
        org.mockito.Mockito.verifyNoInteractions(invoices,payments,ledgerLines,sales,estateCharges);
    }

    private PMSInvoice invoice(String reference) {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setRef(reference);
        invoice.setAmount(1_000);
        invoice.setPendingAmount(500);
        invoice.setCurrency("KES");
        invoice.setDueDate(LocalDate.now());
        invoice.setActive(true);
        return invoice;
    }
}
