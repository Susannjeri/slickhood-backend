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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
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
    @InjectMocks ReportService service;

    @BeforeEach
    void user() {
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
        when(invoices.findForReport(eq(41L), eq(false), any(), any(), any(Pageable.class))).thenReturn(List.of(invoice));

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
}
