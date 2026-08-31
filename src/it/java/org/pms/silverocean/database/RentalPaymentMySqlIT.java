package org.pms.silverocean.database;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
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
import org.pms.silverocean.service.security.KeyDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in persistence certification. It never connects to a configured SlickHood database: Testcontainers
 * creates and destroys a dedicated MySQL database. With no Docker runtime the suite is explicitly skipped.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(RentalPaymentMySqlIT.TestKeyConfiguration.class)
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "helpdesk.ai.enabled=false",
        "lease.documents.legal-review-required=true",
        "garage.s3.access.key=test-access-key",
        "garage.s3.secret.key=test-secret-key",
        "garage.bootstrap.enabled=false"
})
@Transactional
class RentalPaymentMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("slickhood_integration")
            .withUsername("slickhood_test")
            .withPassword("slickhood_test_only");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driverClassName", MYSQL::getDriverClassName);
        registry.add("audit.datasource.mysql.url", MYSQL::getJdbcUrl);
        registry.add("audit.datasource.mysql.username", MYSQL::getUsername);
        registry.add("audit.datasource.mysql.password", MYSQL::getPassword);
        registry.add("audit.datasource.mysql.driverClassName", MYSQL::getDriverClassName);
        registry.add("whatsapp.phoneNumberId", () -> "test");
        registry.add("whatsapp.accessToken", () -> "test");
        registry.add("whatsapp.verifyToken", () -> "test");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestKeyConfiguration {
        @Bean
        @Primary
        KeyDao integrationTestKeyDao() {
            KeyDao keyDao = mock(KeyDao.class);
            byte[] testKey = Base64.getEncoder().encode(new byte[32]);
            when(keyDao.getActiveSecretKey()).thenReturn(testKey);
            when(keyDao.getOldKeys()).thenReturn(Set.of());
            return keyDao;
        }
    }

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
        assertEquals(4, statement.size());
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
