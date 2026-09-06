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
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()
                && (!Boolean.parseBoolean(setting("SLICKHOOD_TEST_MYSQL_ALLOW_RESET"))
                || !EXTERNAL_URL.matches("jdbc:(?:mysql|mariadb)://(?:127\\.0\\.0\\.1|localhost):[0-9]+/slickhood_(?:rehearsal|integration|test)_[a-zA-Z0-9_]+"))) {
            throw new IllegalArgumentException("Repository DDL tests require an explicitly resettable, loopback disposable database");
        }
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
    static void stopContainer(org.springframework.context.ApplicationContext context) {
        if (mysql != null) {
            // Hibernate's create-drop cleanup needs the disposable database alive.
            // Stopping it first leaves Spring shutdown waiting on dead connections.
            // Keep the test context active for Spring's after-class listeners.
            context.getBean(jakarta.persistence.EntityManagerFactory.class).close();
            if (context.getBean(javax.sql.DataSource.class) instanceof com.zaxxer.hikari.HikariDataSource pool) {
                pool.close();
            }
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
    @Autowired PropertyOwnershipRepo ownerships;
    @Autowired EstateServiceChargeRepo estateCharges;
    @Autowired PropertyManagerRepo propertyManagers;
    @Autowired PropertyAccountRepo propertyAccounts;
    @Autowired PaymentAccountRepo paymentAccounts;
    @Autowired CustomerWorkspaceRepo workspaces;
    @Autowired WorkspaceMembershipRepo memberships;
    @Autowired SaleTransactionRepo sales;
    @Autowired LeaseDocumentRepo documents;
    @Autowired PropertyListingRepo propertyListings;

    @Test
    void saleWorkspaceAndDocumentPartyQueriesPreserveIsolationAndExpiry() {
        Users owner=user("Sale owner","sale-owner@mysql.test","+254700003011");
        Users buyer=user("Buyer","buyer@mysql.test","+254700003022");
        Users staff=user("Sales staff","sales-staff@mysql.test","+254700003033");
        Property property=new Property();property.setName("Sale Court");property.setRef("SALE-IT-1");
        property.setType("APARTMENT");property.setAddress("Test lane");property.setCurrency("KES");property.setCreatedBy(owner.getId());property.setActive(true);
        property.setManagementMode(org.pms.silverocean.service.property.PMSPropertyManagementMode.SALE);properties.saveAndFlush(property);
        Unit unit=new Unit();unit.setPropertyId(property.getId());unit.setRef("SALE-01");unit.setUnitType("HOUSE");unit.setLeaseMode("SALE");unit.setCurrency("KES");unit.setCreatedBy(owner.getId());unit.setActive(true);units.saveAndFlush(unit);
        SaleTransaction sale=new SaleTransaction();sale.setPropertyId(property.getId());sale.setUnitId(unit.getId());sale.setBuyerUserId(buyer.getId());sale.setSalesAgentUserId(owner.getId());
        sale.setStatus(org.pms.silverocean.service.sales.SaleStatus.OFFERED);sale.setAskingPrice(java.math.BigDecimal.TEN);sale.setOfferAmount(java.math.BigDecimal.TEN);sale.setCurrency("KES");sale.setActive(true);sales.saveAndFlush(sale);
        PropertyManager assignment=new PropertyManager();assignment.setPropertyId(property.getId());assignment.setUserId(staff.getId());assignment.setRoleName("SALES_COORDINATOR");assignment.setInviteId(-9L);assignment.setActive(true);propertyManagers.saveAndFlush(assignment);
        var page=PageRequest.of(0,25);
        assertEquals(1,sales.findViewPageBySalesScope(owner.getId(),true,"SALES_AGENT",null,page).getTotalElements());
        assertEquals(1,sales.findViewPageBySalesScope(staff.getId(),false,"SALES_COORDINATOR",-9L,page).getTotalElements());
        assertEquals(0,sales.findViewPageBySalesScope(staff.getId(),false,"SALES_COORDINATOR",-10L,page).getTotalElements());
        assertEquals(0,sales.findViewPageBySalesScope(staff.getId(),false,"LISTING_AGENT",-9L,page).getTotalElements());
        LeaseDocument d=new LeaseDocument();d.setSaleId(sale.getId());d.setPropertyId(property.getId());d.setUnitId(unit.getId());
        d.setIssuerUserId(owner.getId());d.setRecipientUserId(buyer.getId());d.setTemplateId(1);d.setTemplateVersion(1);d.setName("Test offer");d.setRenderedHtml("<p>Test only</p>");
        d.setDocumentType(org.pms.silverocean.service.leasedocument.LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER);
        d.setStatus(org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.ISSUED);d.setResponseDueDate(LocalDate.now().minusDays(1));d.setActive(true);documents.saveAndFlush(d);
        assertEquals(1,documents.findAccessiblePage(buyer.getId(),null,sale.getId(),null,page).getTotalElements());
        assertEquals(0,documents.findAccessiblePage(staff.getId(),null,sale.getId(),null,page).getTotalElements());
        assertTrue(documents.findAccessibleForUpdate(d.getId(),buyer.getId()).isPresent());
        assertEquals(1,documents.expireSaleOffers(sale.getId(),LocalDate.now()));
        assertFalse(documents.existsOpenForSale(sale.getId(),d.getDocumentType()));
        unit.setAdvertise(true); units.saveAndFlush(unit);
        var listing=new org.pms.silverocean.database.pms.entities.PropertyListing();
        listing.setUnitId(unit.getId());listing.setPublicSlug("sale-integration-listing");listing.setListingType("SALE");
        listing.setHeadline("Test sale only");listing.setDescription("Fixture");listing.setStatus("PUBLISHED");
        listing.setPublisherUserId(owner.getId());listing.setActive(true);listing.setPublishedAt(ZonedDateTime.now());
        propertyListings.saveAndFlush(listing);
        assertTrue(propertyListings.findPublicBySlug(listing.getPublicSlug(),ZonedDateTime.now()).isPresent());
        for (var state : List.of(org.pms.silverocean.service.sales.SaleStatus.RESERVED, org.pms.silverocean.service.sales.SaleStatus.COMPLETED)) {
            sale.setStatus(state);sales.saveAndFlush(sale);
            assertTrue(propertyListings.hasReservedOrCompletedSale(unit.getId()));
            assertTrue(propertyListings.findPublicBySlug(listing.getPublicSlug(),ZonedDateTime.now()).isEmpty());
            assertEquals(0,propertyListings.searchPublic("SALE",null,null,null,null,ZonedDateTime.now(),page).getTotalElements());
            assertTrue(propertyListings.findPublicUnitTypes("SALE",ZonedDateTime.now()).isEmpty());
        }
        sale.setStatus(org.pms.silverocean.service.sales.SaleStatus.CANCELLED);sales.saveAndFlush(sale);
        assertTrue(propertyListings.findPublicBySlug(listing.getPublicSlug(),ZonedDateTime.now()).isPresent());
        property.setManagementMode(org.pms.silverocean.service.property.PMSPropertyManagementMode.SERVICE_CHARGE);properties.saveAndFlush(property);
        assertTrue(propertyListings.findPublicBySlug(listing.getPublicSlug(),ZonedDateTime.now()).isEmpty());
    }

    @Test
    void estateRegistryBillingAndWorkspaceBoundariesUseRealMysqlQueries() {
        Users owner = user("Estate Owner", "estate-owner@mysql.test", "+254700002011");
        Users resident = user("Estate Resident", "resident@mysql.test", "+254700002022");
        Users employee = user("Estate Accountant", "accountant@mysql.test", "+254700002033");
        Property estate = new Property(); estate.setName("Cedar Court"); estate.setRef("ESTATE-IT-1");
        estate.setCurrency("KES"); estate.setType("GATED_ESTATE"); estate.setAddress("Test street");
        estate.setManagementMode(org.pms.silverocean.service.property.PMSPropertyManagementMode.SERVICE_CHARGE);
        estate.setCreatedBy(owner.getId()); estate.setActive(true); properties.saveAndFlush(estate);
        Unit home = new Unit(); home.setPropertyId(estate.getId()); home.setRef("H-01");
        home.setUnitType("HOUSE"); home.setLeaseMode("SERVICE_CHARGE"); home.setCurrency("KES");
        home.setCreatedBy(owner.getId()); home.setActive(true); units.saveAndFlush(home);
        PropertyOwnership ownership = new PropertyOwnership(); ownership.setPropertyId(estate.getId());
        ownership.setUnitId(home.getId()); ownership.setHomeownerUserId(resident.getId());
        ownership.setOwnershipStart(LocalDate.now().minusMonths(1)); ownership.setActive(true);
        ownership.setCreatedBy(owner.getId()); ownerships.saveAndFlush(ownership);
        PMSInvoice invoice = new PMSInvoice(); invoice.setRef("ESTATE-IT-INVOICE");
        invoice.setPropertyId(estate.getId()); invoice.setUnitId(home.getId());
        invoice.setBilledUserId(resident.getId()); invoice.setPayToUserId(owner.getId());
        invoice.setAmount(1500); invoice.setPendingAmount(500); invoice.setCurrency("KES");
        invoice.setBillingType("SERVICE_CHARGE"); invoice.setDueDate(LocalDate.now().minusDays(1));
        invoice.setActive(true); invoices.saveAndFlush(invoice);
        EstateServiceCharge charge = new EstateServiceCharge(); charge.setPropertyId(estate.getId());
        charge.setUnitId(home.getId()); charge.setHomeownerUserId(resident.getId()); charge.setInvoiceId(invoice.getId());
        charge.setAmount(new java.math.BigDecimal("1500.00")); charge.setCurrency("KES");
        charge.setDescription("Security"); charge.setDueDate(invoice.getDueDate()); charge.setActive(true);
        charge.setCreatedBy(owner.getId()); estateCharges.saveAndFlush(charge);
        var page = PageRequest.of(0,25);
        assertEquals(1,ownerships.findPageByEstateScope(owner.getId(),true,"ESTATE_MANAGER",null,estate.getId(),true,page).getTotalElements());
        assertEquals(1,estateCharges.findPageByEstateScope(owner.getId(),true,"ESTATE_MANAGER",null,estate.getId(),page).getTotalElements());
        assertEquals(0,ownerships.findPageByEstateScope(employee.getId(),true,"ESTATE_MANAGER",null,null,true,page).getTotalElements());
        PropertyManager assignment = new PropertyManager(); assignment.setUserId(employee.getId());
        assignment.setPropertyId(estate.getId()); assignment.setInviteId(-7L);
        assignment.setRoleName("PROPERTY_ACCOUNTANT"); assignment.setActive(true); propertyManagers.saveAndFlush(assignment);
        assertEquals(1,ownerships.findPageByEstateScope(employee.getId(),false,"PROPERTY_ACCOUNTANT",-7L,null,true,page).getTotalElements());
        assertEquals(0,ownerships.findPageByEstateScope(employee.getId(),false,"PROPERTY_ACCOUNTANT",-8L,null,true,page).getTotalElements());
        assertEquals(0,estateCharges.findPageByEstateScope(employee.getId(),false,"WORKSPACE_VIEWER",-7L,null,page).getTotalElements());
        assertEquals(0,estateCharges.findPageByEstateScope(employee.getId(),false,"PROPERTY_ACCOUNTANT",-8L,null,page).getTotalElements());
        assertEquals(500.0,estateCharges.findPageByHomeowner(resident.getId(),null,page).getContent().getFirst().pendingAmount());
        assertEquals("OVERDUE",estateCharges.findPageByHomeowner(resident.getId(),null,page).getContent().getFirst().status());
        ownership.setActive(false); ownerships.saveAndFlush(ownership);
        assertEquals(0,ownerships.findPageByHomeowner(resident.getId(),null,true,page).getTotalElements());
        assertEquals(1,ownerships.findPageByHomeowner(resident.getId(),null,null,page).getTotalElements());
        assertEquals(1,estateCharges.findPageByHomeowner(resident.getId(),null,page).getTotalElements(),"Ending ownership preserves access to historical bills");

        PaymentAccount account = new PaymentAccount(); account.setName("Cedar Collections");
        account.setCategory(org.pms.silverocean.service.account.enums.AccountCategory.ESTATE_MANAGEMENT);
        account.setChannel(PaymentChannel.PAYSTACK); account.setCreatedBy(owner.getId()); account.setActive(true);
        paymentAccounts.saveAndFlush(account);
        PropertyAccount link = new PropertyAccount(); link.setPropertyId(estate.getId()); link.setAccountId(account.getId()); link.setActive(true); propertyAccounts.saveAndFlush(link);
        assertEquals(0,propertyAccounts.countVerifiedOperatingAccounts(estate.getId(),account.getCategory()));
        assertTrue(properties.findByIdAndHomeownerInviter(estate.getId(),owner.getId()).isPresent());
        assertTrue(properties.findByIdAndHomeownerInviter(estate.getId(),employee.getId()).isEmpty(),"Finance assignment is not homeowner invitation authority");
        CustomerWorkspace workspace = new CustomerWorkspace(); workspace.setName("Cedar workspace");
        workspace.setOwnerUserId(owner.getId()); workspace.setBusinessArea(org.pms.silverocean.service.teamaccess.TeamBusinessArea.ESTATE_MANAGEMENT);
        workspace.setActive(true); workspaces.saveAndFlush(workspace);
        WorkspaceMembership member = new WorkspaceMembership(); member.setWorkspaceId(workspace.getId());
        member.setUserId(employee.getId()); member.setMemberEmail(employee.getEmail()); member.setActive(true);
        member.setMembershipRole(org.pms.silverocean.service.teamaccess.TeamMembershipRole.ESTATE_OPERATIONS_MANAGER);
        member.setScopeType(org.pms.silverocean.service.teamaccess.TeamScopeType.ENTIRE_WORKSPACE);
        member.setStatus(org.pms.silverocean.service.teamaccess.TeamMembershipStatus.ACTIVE); memberships.saveAndFlush(member);
        assignment.setRoleName("ESTATE_OPERATIONS_MANAGER"); assignment.setInviteId(-member.getId()); propertyManagers.saveAndFlush(assignment);
        assertTrue(properties.findByIdAndHomeownerInviter(estate.getId(),employee.getId()).isPresent());
        member.setStatus(org.pms.silverocean.service.teamaccess.TeamMembershipStatus.SUSPENDED); memberships.saveAndFlush(member);
        assertTrue(properties.findByIdAndHomeownerInviter(estate.getId(),employee.getId()).isEmpty(),"Suspended inviter cannot attach a homeowner even if a stale property assignment exists");
        account.setVerified(true); paymentAccounts.saveAndFlush(account);
        assertEquals(1,propertyAccounts.countVerifiedOperatingAccounts(estate.getId(),account.getCategory()));
        account.setActive(false); paymentAccounts.saveAndFlush(account);
        assertEquals(0,propertyAccounts.countVerifiedOperatingAccounts(estate.getId(),account.getCategory()));
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
        assertEquals(1,leases.findScopedLeases(tenant.getId(),"TENANT",null,PageRequest.of(0,25)).getTotalElements());
        assertEquals(1,leases.findScopedLeases(landlord.getId(),"LANDLORD",null,PageRequest.of(0,25)).getTotalElements());
        assertEquals(0,leases.findScopedLeases(landlord.getId(),"TENANT",null,PageRequest.of(0,25)).getTotalElements());

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
