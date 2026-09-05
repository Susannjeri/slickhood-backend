package org.pms.silverocean.database;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIf;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.ledger.FinancialLedgerService;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.DockerClientFactory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in persistence and migration certification. It never reads the application's configured datasource.
 * CI normally gets a disposable database from Testcontainers. A release runner without Docker can provide
 * an explicitly isolated database through SLICKHOOD_TEST_MYSQL_* environment variables. With neither option
 * available the test is explicitly skipped.
 */
@EnabledIf("mysqlAvailable")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@Transactional
class RentalPaymentMySqlIT {
    private static final String EXTERNAL_URL = setting("SLICKHOOD_TEST_MYSQL_URL");
    private static final String EXTERNAL_USERNAME = setting("SLICKHOOD_TEST_MYSQL_USERNAME");
    private static final String EXTERNAL_PASSWORD = setting("SLICKHOOD_TEST_MYSQL_PASSWORD");
    private static MySQLContainer<?> mysql;

    static boolean mysqlAvailable() {
        return EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()
                || DockerClientFactory.instance().isDockerAvailable();
    }

    private static String setting(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? System.getenv(name) : value;
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL == null || EXTERNAL_URL.isBlank()) {
            mysql = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("slickhood_integration")
                    .withUsername("slickhood_test")
                    .withPassword("slickhood_test_only");
            mysql.start();
        }

        String url = mysql == null ? EXTERNAL_URL : mysql.getJdbcUrl();
        String username = mysql == null ? EXTERNAL_USERNAME : mysql.getUsername();
        String password = mysql == null ? EXTERNAL_PASSWORD : mysql.getPassword();
        String driver = mysql == null ? externalDriver(url) : mysql.getDriverClassName();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driverClassName", () -> driver);
        // Both Testcontainers and the explicitly supplied release database are disposable.
        // Let Hibernate create the complete persistence model so this test exercises repository
        // behaviour independently of the separate production-schema migration rehearsal.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("audit.datasource.mysql.url", () -> url);
        registry.add("audit.datasource.mysql.username", () -> username);
        registry.add("audit.datasource.mysql.password", () -> password);
        registry.add("audit.datasource.mysql.driverClassName", () -> driver);
        registry.add("whatsapp.phoneNumberId", () -> "test");
        registry.add("whatsapp.accessToken", () -> "test");
        registry.add("whatsapp.verifyToken", () -> "test");
    }

    private static String externalDriver(String url) {
        return url != null && url.startsWith("jdbc:mysql:")
                ? "com.mysql.cj.jdbc.Driver"
                : "org.mariadb.jdbc.Driver";
    }

    @AfterAll
    static void stopContainer() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Autowired UserRepo users;
    @Autowired PropertyRepo properties;
    @Autowired UnitRepo units;
    @Autowired UnitTenantRepo tenancies;
    @Autowired LeaseRepo leases;
    @Autowired PMSInvoiceRepo invoices;
    @Autowired PMSPaymentRepo payments;
    @Autowired FinancialJournalRepo journals;
    @Autowired FinancialLedgerLineRepo ledgerLines;

    @Test
    void realMysqlRepositoriesPersistAndReconcileTheRentalPaymentExactlyOnce() {
        Users landlord = user("Seeded Landlord", "landlord@mysql.test", "+254700001011");
        Users tenant = user("Seeded Tenant", "tenant@mysql.test", "+254700001022");

        Property property = new Property();
        property.setName("MySQL Heights");
        property.setType("APARTMENT");
        property.setAddress("Integration Lane");
        property.setCurrency("KES");
        property.setRef("PROP-MYSQL-1");
        property.setCreatedBy(landlord.getId());
        property.setActive(true);
        property = properties.saveAndFlush(property);

        Unit unit = new Unit();
        unit.setPropertyId(property.getId());
        unit.setRef("A-12");
        unit.setUnitType("TWO_BEDROOM");
        unit.setLeaseMode("RENT");
        unit.setPrice(25_000);
        unit.setCurrency("KES");
        unit.setOccupied(true);
        unit.setCreatedBy(landlord.getId());
        unit.setActive(true);
        unit = units.saveAndFlush(unit);

        // MySQL rejects SELECT DISTINCT entity queries ordered by a joined column
        // unless the ordering expression is selected. This exercises the exact
        // property/unit report query used by the production unit-list endpoint.
        List<Unit> reportUnits = units.findForReport(landlord.getId(), false, PageRequest.of(0, 20));
        assertEquals(List.of(unit.getId()), reportUnits.stream().map(Unit::getId).toList());

        UnitTenant tenancy = new UnitTenant();
        tenancy.setUnitId(unit.getId());
        tenancy.setUserId(tenant.getId());
        tenancy.setInviteId(7001L);
        tenancy.setLeaseAccepted(true);
        tenancy.setActive(true);
        tenancy = tenancies.saveAndFlush(tenancy);

        Lease lease = new Lease();
        lease.setTenantId(tenancy.getId());
        lease.setName("Seeded residential lease");
        lease.setLeaseMode("RENT");
        lease.setLeaseDate(LocalDate.now());
        lease.setMoveInDate(LocalDate.now());
        lease.setMoveOutDate(LocalDate.now().plusYears(1));
        lease.setPrice(25_000);
        lease.setCurrency("KES");
        lease.setSigned(true);
        lease.setTenantSignedDate(java.time.LocalDateTime.now());
        lease.setManagerSignedDate(java.time.LocalDateTime.now());
        lease.setSignedByManagerId(landlord.getId());
        lease.setCreatedBy(landlord.getId());
        lease.setActive(true);
        lease = leases.saveAndFlush(lease);

        assertEquals(property.getId(), unit.getPropertyId());
        assertEquals(unit.getId(), tenancy.getUnitId());
        assertEquals(tenancy.getId(), lease.getTenantId());

        FinancialLedgerService ledger = new FinancialLedgerService(journals, ledgerLines);
        InvoiceDao invoiceDao = new InvoiceDao(invoices, ledger);
        PMSInvoice invoice = new PMSInvoice();
        invoice.setUnitId(unit.getId());
        invoice.setPropertyId(property.getId());
        invoice.setBilledUserId(tenant.getId());
        invoice.setPayToUserId(landlord.getId());
        invoice.setAmount(25_000);
        invoice.setPendingAmount(25_000);
        invoice.setCurrency("KES");
        invoice.setBillingType("RENTAL");
        invoice.setDescription("Monthly rent".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invoice.setHtmlDescription("Monthly rent".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invoice.setCustomerEmail(tenant.getEmail());
        invoice.setCustomerPhoneNumber(tenant.getPhoneNumber());
        invoice.setDueDate(LocalDate.now().plusDays(5));
        invoice.setActive(true);
        invoiceDao.createInvoice(invoice);
        invoices.flush();

        PMSPayment payment = new PMSPayment(invoice, tenant.getFullName(), 71L);
        payment.setChannel(PaymentChannel.PAYSTACK.getName());
        payment.setCategory(TransactionCategory.CARD_PAYMENT.name());
        payment.setStatus(TransactionCategory.CARD_PAYMENT.getSuccessString());
        payment.setStatusDesc("Approved");
        payment.setThirdPartyTransId("PAYSTACK-MYSQL-998877");
        payment.setInProgress(false);
        payment = payments.saveAndFlush(payment);

        I18NService i18n = mock(I18NService.class);
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Ref %s %s %.2f invoice %s at %s");
        UpdatePaymentService updater = new UpdatePaymentService(mock(NotificationService.class), invoiceDao, i18n,
                mock(DomainEventOutboxPublisher.class), ledger);
        updater.setInvoiceToPaid(invoice, payment.getThirdPartyTransId(), payment.getAmount());
        invoices.flush();
        ledgerLines.flush();

        PMSInvoice paid = invoices.findByRef(invoice.getRef()).orElseThrow();
        assertTrue(paid.isPaid());
        assertEquals(0.0, paid.getPendingAmount());
        assertTrue(payment.isCompletedSuccessfully());
        assertTrue(payments.findByIdForAuthorizedUser(payment.getId(), landlord.getId()).isPresent());
        assertTrue(payments.findByIdForAuthorizedUser(payment.getId(), tenant.getId()).isPresent());

        List<PMSPayment> reconciliation = payments.findForReport(landlord.getId(), false,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), PageRequest.of(0, 20));
        assertEquals(List.of(payment.getId()), reconciliation.stream().map(PMSPayment::getId).toList());
        List<FinancialLedgerLine> statement = ledgerLines.findForStatement(landlord.getId(), false,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), PageRequest.of(0, 20));
        assertEquals(2, statement.size(), "the landlord statement contains one line from each balanced journal");
        assertEquals(statement.stream().map(FinancialLedgerLine::getDebit).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                statement.stream().map(FinancialLedgerLine::getCredit).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        updater.setInvoiceToPaid(paid, payment.getThirdPartyTransId(), payment.getAmount());
        assertEquals(2, journals.count(), "callback replay must not create a duplicate journal");
        assertEquals(4, ledgerLines.count(), "callback replay must not create duplicate ledger lines");
    }

    private Users user(String name, String email, String phone) {
        Users user = new Users();
        user.setFullName(name);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPassword("not-used-in-integration-test");
        user.setActive(true);
        return users.saveAndFlush(user);
    }
}
