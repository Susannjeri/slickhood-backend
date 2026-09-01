package org.pms.silverocean.service.payment.platforms.paystack;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.CallBackController;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.sms.SMSService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.*;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.reports.ReportService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Controlled, provider-free integration journey. The property graph and financial records are seeded
 * in memory, while the real invoice, Paystack callback, payment-update, ledger and report services run.
 */
class RentalPaymentReconciliationJourneyTest {
    private static final long LANDLORD_ID = 11L;
    private static final long TENANT_ID = 22L;
    private static final String SECRET = "sk_test_seeded_journey";

    @Test
    void propertyToLeaseToSignedCallbackProducesOneBalancedReconciledReceiptEligiblePayment() throws Exception {
        Property property = property();
        Unit unit = unit(property);
        UnitTenant tenancy = tenancy(unit);
        Lease lease = lease(tenancy);

        assertEquals(property.getId(), unit.getPropertyId());
        assertEquals(unit.getId(), tenancy.getUnitId());
        assertEquals(tenancy.getId(), lease.getTenantId());
        assertTrue(lease.isSigned());

        PMSInvoiceRepo invoiceRepo = mock(PMSInvoiceRepo.class);
        AtomicReference<PMSInvoice> storedInvoice = new AtomicReference<>();
        when(invoiceRepo.save(any(PMSInvoice.class))).thenAnswer(invocation -> {
            PMSInvoice invoice = invocation.getArgument(0);
            if (invoice.getId() == null) invoice.setId(501L);
            if (invoice.getCreatedOn() == null) invoice.setCreatedOn(ZonedDateTime.now());
            storedInvoice.set(invoice);
            return invoice;
        });
        doAnswer(invocation -> {
            storedInvoice.get().setRef(invocation.getArgument(1));
            return 1;
        }).when(invoiceRepo).updateInvoiceRef(anyLong(), anyString());
        when(invoiceRepo.findByIdForUpdate(501L)).thenAnswer(ignored -> Optional.ofNullable(storedInvoice.get()));
        when(invoiceRepo.findByRef(anyString())).thenAnswer(ignored -> Optional.ofNullable(storedInvoice.get()));

        FinancialJournalRepo journalRepo = mock(FinancialJournalRepo.class);
        FinancialLedgerLineRepo lineRepo = mock(FinancialLedgerLineRepo.class);
        Set<String> eventKeys = new LinkedHashSet<>();
        List<FinancialJournal> journals = new ArrayList<>();
        List<FinancialLedgerLine> ledgerLines = new ArrayList<>();
        AtomicLong journalSequence = new AtomicLong(800);
        AtomicLong lineSequence = new AtomicLong(900);
        when(journalRepo.existsByEventKey(anyString())).thenAnswer(i -> eventKeys.contains(i.getArgument(0)));
        when(journalRepo.save(any(FinancialJournal.class))).thenAnswer(invocation -> {
            FinancialJournal journal = invocation.getArgument(0);
            journal.setId(journalSequence.incrementAndGet());
            journal.setCreatedOn(ZonedDateTime.now());
            eventKeys.add(journal.getEventKey());
            journals.add(journal);
            return journal;
        });
        when(lineRepo.saveAll(any())).thenAnswer(invocation -> {
            Iterable<FinancialLedgerLine> incoming = invocation.getArgument(0);
            List<FinancialLedgerLine> saved = new ArrayList<>();
            incoming.forEach(line -> {
                line.setId(lineSequence.incrementAndGet());
                line.setCreatedOn(ZonedDateTime.now());
                ledgerLines.add(line);
                saved.add(line);
            });
            return saved;
        });

        FinancialLedgerService ledger = new FinancialLedgerService(journalRepo, lineRepo);
        InvoiceDao invoices = new InvoiceDao(invoiceRepo, ledger);
        PMSInvoice invoice = invoice(unit, tenancy);
        invoices.createInvoice(invoice);
        assertEquals("INV-1F5", invoice.getRef());
        assertEquals("INVOICE_ISSUED", journals.getFirst().getEventType());

        PMSPaymentRepo paymentRepo = mock(PMSPaymentRepo.class);
        AtomicReference<PMSPayment> storedPayment = new AtomicReference<>();
        when(paymentRepo.save(any(PMSPayment.class))).thenAnswer(invocation -> {
            PMSPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) payment.setId(601L);
            if (payment.getCreatedOn() == null) payment.setCreatedOn(ZonedDateTime.now());
            storedPayment.set(payment);
            return payment;
        });
        when(paymentRepo.findById(601L)).thenAnswer(ignored -> Optional.ofNullable(storedPayment.get()));

        PaymentDao payments = new PaymentDao(paymentRepo);
        NotificationService notifications = mock(NotificationService.class);
        I18NService i18n = mock(I18NService.class);
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Ref %s %s %.2f invoice %s at %s");
        UpdatePaymentService updater = new UpdatePaymentService(notifications, invoices, i18n,
                mock(DomainEventOutboxPublisher.class), ledger);

        Users tenant = new Users();
        tenant.setId(TENANT_ID);
        tenant.setFullName("Seeded Tenant");
        tenant.setEmail("tenant@example.test");
        UserDao users = mock(UserDao.class);
        when(users.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        AtomicLong activeUser = new AtomicLong(TENANT_ID);
        when(users.getUserId()).thenAnswer(ignored -> activeUser.get());
        when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);

        ParamService params = mock(ParamService.class);
        when(params.getParamByAccountIdAndType(anyLong(), any(), anyLong())).thenReturn("ACCT_seeded_landlord");
        RestTemplateService http = mock(RestTemplateService.class);
        PaystackPlatform.PaystackInitializeResponse initialized = new PaystackPlatform.PaystackInitializeResponse(
                true, "Authorization URL created",
                new PaystackPlatform.PaystackInitializeData("https://pay.example.test/seeded", "access", "601"));
        doReturn(initialized).when(http).sendPostRequest(anyString(), any(), any(), eq(PaystackPlatform.PaystackInitializeResponse.class));
        PaystackPlatform.PaystackVerifyResponse verified = new PaystackPlatform.PaystackVerifyResponse(
                true, "Verified", new PaystackPlatform.PaystackTransaction(
                998877L, "success", "601", 2_500_000L, "KES", "Approved"));
        doReturn(verified).when(http).sendGetRequest(anyString(), any(), eq(PaystackPlatform.PaystackVerifyResponse.class));

        PaystackPlatform paystack = new PaystackPlatform(updater, users, payments, params, http,
                mock(EventService.class), new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(paystack, "enabled", true);
        ReflectionTestUtils.setField(paystack, "secretKey", SECRET);
        ReflectionTestUtils.setField(paystack, "apiUrl", "https://api.paystack.test");
        ReflectionTestUtils.setField(paystack, "callbackUrl", "https://slickhood.test/payment/callback");
        ReflectionTestUtils.setField(paystack, "defaultCurrency", "KES");
        ReflectionTestUtils.setField(paystack, "configuredChannels", "card,mobile_money");
        ReflectionTestUtils.setField(paystack, "feeBearer", "subaccount");

        var initialization = paystack.processPayment(invoice, null, 71L);
        assertTrue(initialization.success());
        assertTrue(invoice.isTransactionInProgress());
        assertEquals("initialized", storedPayment.get().getStatus());

        PaymentPlatformFactory factory = mock(PaymentPlatformFactory.class);
        when(factory.getPlatform(PaymentChannel.PAYSTACK)).thenReturn(paystack);
        CallBackController callbacks = new CallBackController(factory, mock(SMSService.class));
        ReflectionTestUtils.setField(callbacks, "paystackSecretKey", SECRET);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        String body = "{\"event\":\"charge.success\",\"data\":{\"id\":998877,\"status\":\"success\",\"reference\":\"601\",\"amount\":2500000,\"currency\":\"KES\",\"gateway_response\":\"Approved\"}}";

        var callback = callbacks.receivePaystackCallback(request, hmac(body), body);
        assertEquals(HttpStatus.OK, callback.getStatusCode());
        assertTrue(invoice.isPaid());
        assertFalse(invoice.isTransactionInProgress());
        assertEquals(0.0, invoice.getPendingAmount());
        assertEquals("successful", storedPayment.get().getStatus());
        assertEquals("998877", storedPayment.get().getThirdPartyTransId());
        assertFalse(storedPayment.get().isInProgress());
        assertTrue(storedPayment.get().isCompletedSuccessfully(), "a reconciled Paystack payment must be receipt eligible");

        assertEquals(2, journals.size());
        assertEquals(List.of("INVOICE_ISSUED", "PAYMENT_APPLIED"), journals.stream().map(FinancialJournal::getEventType).toList());
        assertEquals(4, ledgerLines.size());
        assertBalanced(ledgerLines, "KES", new BigDecimal("50000.00"));

        callbacks.receivePaystackCallback(request, hmac(body), body);
        verify(http, times(1)).sendGetRequest(anyString(), any(), eq(PaystackPlatform.PaystackVerifyResponse.class));
        assertEquals(2, journals.size(), "the duplicate callback must not create another journal");
        assertEquals(4, ledgerLines.size(), "the duplicate callback must not create another ledger posting");

        when(invoiceRepo.findForReport(eq(LANDLORD_ID), eq(false), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(invoice));
        when(paymentRepo.findForReport(eq(LANDLORD_ID), eq(false), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(storedPayment.get()));
        when(lineRepo.findForStatement(eq(LANDLORD_ID), eq(false), any(), any(), any(Pageable.class)))
                .thenReturn(ledgerLines);
        activeUser.set(LANDLORD_ID);
        ReportService reports = reports(users, invoiceRepo, paymentRepo, lineRepo);
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();

        var reconciliation = reports.generate("PAYMENT_RECONCILIATION", from, to);
        assertEquals(1L, reconciliation.metrics().get("Successful"));
        assertEquals(0L, reconciliation.metrics().get("Pending"));
        assertEquals(0L, reconciliation.metrics().get("Exceptions"));
        assertEquals(invoice.getRef(), reconciliation.rows().getFirst().get("Reference"));
        assertEquals("998877", reconciliation.rows().getFirst().get("Transaction"));

        var statement = reports.generate("ACCOUNT_STATEMENT", from, to);
        assertEquals(4, statement.metrics().get("Entries"));
        var collections = reports.generate("INVOICE_COLLECTIONS", from, to);
        assertEquals("PAID", collections.rows().getFirst().get("Status"));
        assertEquals(new BigDecimal("0.00"), collections.rows().getFirst().get("Outstanding"));
    }

    private static Property property() {
        Property property = new Property();
        property.setId(101L);
        property.setCreatedBy(LANDLORD_ID);
        property.setName("Seeded Heights");
        property.setCurrency("KES");
        property.setActive(true);
        return property;
    }

    private static Unit unit(Property property) {
        Unit unit = new Unit();
        unit.setId(201L);
        unit.setPropertyId(property.getId());
        unit.setCreatedBy(property.getCreatedBy());
        unit.setRef("A-12");
        unit.setCurrency(property.getCurrency());
        unit.setPrice(25_000);
        unit.setOccupied(true);
        unit.setActive(true);
        return unit;
    }

    private static UnitTenant tenancy(Unit unit) {
        UnitTenant tenancy = new UnitTenant();
        tenancy.setId(301L);
        tenancy.setUnitId(unit.getId());
        tenancy.setUserId(TENANT_ID);
        tenancy.setLeaseAccepted(true);
        tenancy.setActive(true);
        return tenancy;
    }

    private static Lease lease(UnitTenant tenancy) {
        Lease lease = new Lease();
        lease.setId(401L);
        lease.setTenantId(tenancy.getId());
        lease.setCreatedBy(LANDLORD_ID);
        lease.setSigned(true);
        lease.setPrice(25_000);
        lease.setCurrency("KES");
        lease.setMoveInDate(LocalDate.now().minusMonths(1));
        lease.setMoveOutDate(LocalDate.now().plusMonths(11));
        lease.setActive(true);
        return lease;
    }

    private static PMSInvoice invoice(Unit unit, UnitTenant tenancy) {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setUnitId(unit.getId());
        invoice.setPropertyId(unit.getPropertyId());
        invoice.setBilledUserId(tenancy.getUserId());
        invoice.setPayToUserId(unit.getCreatedBy());
        invoice.setAmount(25_000);
        invoice.setPendingAmount(25_000);
        invoice.setCurrency(unit.getCurrency());
        invoice.setBillingType("RENTAL");
        invoice.setDescription("Monthly rent".getBytes(StandardCharsets.UTF_8));
        invoice.setHtmlDescription("<tr><td>Monthly rent</td><td>25000</td></tr>".getBytes(StandardCharsets.UTF_8));
        invoice.setCustomerEmail("tenant@example.test");
        invoice.setCustomerPhoneNumber("+254700000022");
        invoice.setDueDate(LocalDate.now().plusDays(5));
        invoice.setActive(true);
        return invoice;
    }

    private static ReportService reports(UserDao users, PMSInvoiceRepo invoices, PMSPaymentRepo payments,
                                         FinancialLedgerLineRepo ledgerLines) {
        return new ReportService(users, invoices, payments, mock(UnitRepo.class), mock(VisitorRepo.class),
                mock(SaleTransactionRepo.class), mock(EstateServiceChargeRepo.class), mock(ServiceBookingRepo.class),
                mock(SokoOrderRepo.class), ledgerLines, mock(LeaseRepo.class), mock(UserSubscriptionRepo.class),
                mock(AffiliateCommissionRepo.class), mock(KycCaseRepo.class), mock(NotificationRepo.class),
                mock(GateDeviceRepo.class), mock(MaintenanceWorkOrderRepo.class));
    }

    private static void assertBalanced(List<FinancialLedgerLine> lines, String currency, BigDecimal expected) {
        assertTrue(lines.stream().allMatch(line -> currency.equals(line.getCurrency())));
        BigDecimal debits = lines.stream().map(FinancialLedgerLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().map(FinancialLedgerLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(expected, debits);
        assertEquals(expected, credits);
    }

    private static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder signature = new StringBuilder();
        for (byte value : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) signature.append(String.format("%02x", value));
        return signature.toString();
    }
}
