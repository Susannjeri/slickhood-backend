package org.pms.silverocean.service.sales;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.*;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.estate.EstateService;
import org.pms.silverocean.service.invites.InviteService;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {
    @Mock SaleTransactionRepo sales;
    @Mock PropertyRepo properties;
    @Mock UnitRepo units;
    @Mock UserDao users;
    @Mock EstateService estates;
    @Mock SaleMilestoneRepo milestones;
    @Mock InviteService invites;
    @Mock NotificationService notifications;
    @Mock I18NService i18n;
    @Mock LeaseDocumentRepo documents;
    @Mock PMSInvoiceRepo invoices;
    @Mock InvoiceService invoiceService;
    @Mock SalesAccessService access;
    SalesService service;
    Property property;
    Unit unit;
    Users buyer;

    @BeforeEach
    void setUp() {
        service = new SalesService(sales, properties, units, users, estates, milestones, invites, notifications, i18n,
                documents, invoices, invoiceService, access);
        property = new Property(); property.setId(11L); property.setActive(true); property.setCreatedBy(100L);
        property.setManagementMode(PMSPropertyManagementMode.SALE);
        unit = new Unit(); unit.setId(77L); unit.setPropertyId(11L); unit.setActive(true); unit.setLeaseMode("SALE"); unit.setCurrency("KES");
        buyer = new Users(); buyer.setId(200L); buyer.setActive(true); buyer.setEmail("buyer@example.com");
        lenient().when(properties.findById(11L)).thenReturn(Optional.of(property));
    }

    @Test
    void salesWorkspaceOwnerCreatesOnlySaleModeUnitTransactions() {
        when(users.getUserId()).thenReturn(100L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(users.findById(200L)).thenReturn(Optional.of(buyer));
        when(units.findAndLockById(77L)).thenReturn(Optional.of(unit));
        when(sales.save(any())).thenAnswer(invocation -> {
            SaleTransaction value = invocation.getArgument(0); value.setId(1L); return value;
        });

        SaleTransaction created = service.create(new CreateSaleRequest(11L, 77L, 200L, null, new BigDecimal("15000000"), "kes", "  direct sale  "));

        assertEquals("KES", created.getCurrency());
        assertEquals("direct sale", created.getNotes());
        verify(invites).createBuyerInvite(1L, "buyer@example.com");
        assertEquals(SaleStatus.LEAD, created.getStatus());
        verify(sales).existsByUnitIdAndActiveTrueAndStatusNot(77L, SaleStatus.CANCELLED);
    }

    @Test
    void secondActiveSaleForSameUnitIsRejectedWhileUnitIsLocked() {
        when(users.getUserId()).thenReturn(100L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(users.findById(200L)).thenReturn(Optional.of(buyer));
        when(units.findAndLockById(77L)).thenReturn(Optional.of(unit));
        when(sales.existsByUnitIdAndActiveTrueAndStatusNot(77L, SaleStatus.CANCELLED)).thenReturn(true);

        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.create(
                new CreateSaleRequest(11L, 77L, 200L, null, BigDecimal.TEN, "KES", null)));

        assertEquals(ResponseCode.DATA_INTEGRITY_VIOLATION, exception.getResponseCode());
        verify(sales, never()).save(any());
    }

    @Test
    void unregisteredBuyerIsStoredAsPendingAndReceivesBoundInvite() {
        when(users.getUserId()).thenReturn(100L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(users.findByEmail("newbuyer@example.com")).thenReturn(Optional.empty());
        when(units.findAndLockById(77L)).thenReturn(Optional.of(unit));
        when(sales.save(any())).thenAnswer(invocation -> {
            SaleTransaction value = invocation.getArgument(0); value.setId(2L); return value;
        });

        SaleTransaction created = service.create(new CreateSaleRequest(11L, 77L, null,
                " NewBuyer@Example.com ", BigDecimal.TEN, "KES", null));

        assertEquals("newbuyer@example.com", created.getInvitedBuyerEmail());
        assertEquals(null, created.getBuyerUserId());
        verify(invites).createBuyerInvite(2L, "newbuyer@example.com");
    }

    @Test
    void delegatedSalesEmployeeSeesSharedPropertyPipelineWithBoundedPaging() {
        when(access.selectedAssignmentId()).thenReturn(null);
        PageRequest bounded = PageRequest.of(0, 100);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(users.hasPermission(Permission.VIEW_SALE_PIPELINE)).thenReturn(true);
        when(sales.findViewPageBySalesScope(300L, false, PMSRole.SALES_COORDINATOR.name(), null, bounded)).thenReturn(new PageImpl<>(List.of(), bounded, 0));

        service.list(PageRequest.of(0, 500));

        verify(sales).findViewPageBySalesScope(300L, false, PMSRole.SALES_COORDINATOR.name(), null, bounded);
    }

    @Test
    void delegatedSalesEmployeeCanRecordOfferButCannotAcceptForBuyer() {
        SaleTransaction sale = sale(SaleStatus.LEAD);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleTransaction offered = service.update(1L, new UpdateSaleRequest(SaleStatus.OFFERED, new BigDecimal("14000000"), "Buyer offer"));
        assertEquals(SaleStatus.OFFERED, offered.getStatus());

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> service.update(1L, new UpdateSaleRequest(SaleStatus.RESERVED, null, null)));
        assertEquals(ResponseCode.SALE_INVALID_TRANSITION, exception.getResponseCode());
    }

    @Test
    void buyerAcceptanceRequiresTheSignedSaleLetterOfOffer() {
        SaleTransaction sale = sale(SaleStatus.OFFERED); sale.setOfferAmount(new BigDecimal("14000000"));
        when(users.getUserId()).thenReturn(200L);
        when(users.getActiveRole()).thenReturn(PMSRole.BUYER);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(documents.existsBySaleIdAndDocumentTypeAndStatusAndActiveTrue(1L,
                org.pms.silverocean.service.leasedocument.LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER,
                org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.SIGNED)).thenReturn(true);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleTransaction accepted = service.acceptOffer(1L);

        assertEquals(SaleStatus.RESERVED, accepted.getStatus());
        assertNotNull(accepted.getOfferAcceptedAt());
    }

    @Test
    void milestoneCannotReferenceAnUnrelatedDocument() {
        SaleTransaction sale = sale(SaleStatus.DUE_DILIGENCE);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(documents.findByIdAndPropertyIdAndUnitIdAndActiveTrue(999L, 11L, 77L)).thenReturn(Optional.empty());

        PMSCustomException exception = assertThrows(PMSCustomException.class, () -> service.addMilestone(1L,
                new SaleMilestoneModels.Create(SaleMilestoneModels.Type.DUE_DILIGENCE_CHECK,
                        SaleMilestoneModels.Status.COMPLETED, null, null, 999L, null)));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, exception.getResponseCode());
        verify(milestones, never()).save(any());
    }

    @Test
    void completionRequiresEvidenceAndTransfersOwnershipInSameTransaction() {
        SaleTransaction sale = sale(SaleStatus.COMPLETION);
        stubSettledEscrow(sale);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.LISTING_AGENT);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(milestones.existsBySaleIdAndMilestoneTypeAndStatus(eq(1L), anyString(), eq("COMPLETED"))).thenReturn(true);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleTransaction completed = service.update(1L, new UpdateSaleRequest(SaleStatus.COMPLETED, null, null));

        assertEquals(SaleStatus.COMPLETED, completed.getStatus());
        verify(estates).transferFromSale(11L, 77L, 200L, 1L);
    }

    @Test
    void escrowInvoiceIsCreatedForTheAcceptedBuyerAndCappedAtTheOffer() {
        SaleTransaction sale = sale(SaleStatus.RESERVED);
        sale.setOfferAmount(new BigDecimal("14000000"));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(501L); invoice.setRef("INV-1F5");
        invoice.setAmount(1400000); invoice.setPendingAmount(1400000); invoice.setCurrency("KES");
        invoice.setDueDate(java.time.LocalDate.now().plusDays(7)); invoice.setActive(true);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(invoiceService.createSaleInvoice(eq(77L), eq(200L), eq(100L), eq(81L), anyMap(), any()))
                .thenReturn(invoice);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EscrowInvoiceModels.View created = service.createEscrowInvoice(1L,
                new EscrowInvoiceModels.Create(new BigDecimal("1400000"), 81L));

        assertEquals(501L, created.invoiceId());
        assertEquals(501L, sale.getEscrowInvoiceId());
        assertEquals(new BigDecimal("1400000"), sale.getEscrowRequiredAmount());
    }

    @Test
    void manuallyTypedEscrowReferenceCannotCompleteTheMilestone() {
        SaleTransaction sale = sale(SaleStatus.AGREEMENT);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);

        assertThrows(PMSCustomException.class, () -> service.addMilestone(1L,
                new SaleMilestoneModels.Create(SaleMilestoneModels.Type.ESCROW_FUNDED,
                        SaleMilestoneModels.Status.COMPLETED, new BigDecimal("1000"), "typed-reference", null, null)));

        verify(milestones, never()).save(any());
    }

    @Test
    void onlyTheLinkedFullyPaidInvoiceCompletesEscrow() {
        SaleTransaction sale = sale(SaleStatus.AGREEMENT);
        sale.setOfferAmount(new BigDecimal("14000000"));
        sale.setEscrowInvoiceId(501L); sale.setEscrowRequiredAmount(new BigDecimal("1400000"));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(501L); invoice.setRef("INV-1F5");
        invoice.setAmount(1400000); invoice.setPendingAmount(0); invoice.setCurrency("KES");
        invoice.setPropertyId(11L); invoice.setUnitId(77L); invoice.setBilledUserId(200L);
        invoice.setBillingType("SALE"); invoice.setPaid(true); invoice.setActive(true); invoice.setPayToUserId(100L);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(invoices.findByIdForUpdate(501L)).thenReturn(Optional.of(invoice));
        when(milestones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleMilestone recorded = service.addMilestone(1L, new SaleMilestoneModels.Create(
                SaleMilestoneModels.Type.ESCROW_FUNDED, SaleMilestoneModels.Status.COMPLETED,
                null, null, null, null));

        assertEquals("INV-1F5", recorded.getExternalReference());
        assertEquals(new BigDecimal("1400000.0"), recorded.getAmount());
    }

    @Test
    void unpaidLinkedInvoiceCannotCompleteEscrow() {
        SaleTransaction sale = sale(SaleStatus.AGREEMENT);
        sale.setEscrowInvoiceId(501L); sale.setEscrowRequiredAmount(new BigDecimal("1400000"));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(501L); invoice.setRef("INV-1F5");
        invoice.setAmount(1400000); invoice.setPendingAmount(1400000); invoice.setCurrency("KES");
        invoice.setPropertyId(11L); invoice.setUnitId(77L); invoice.setBilledUserId(200L);
        invoice.setBillingType("SALE"); invoice.setPaid(false); invoice.setActive(true);
        when(users.getUserId()).thenReturn(300L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(invoices.findByIdForUpdate(501L)).thenReturn(Optional.of(invoice));

        assertThrows(PMSCustomException.class, () -> service.addMilestone(1L,
                new SaleMilestoneModels.Create(SaleMilestoneModels.Type.ESCROW_FUNDED,
                        SaleMilestoneModels.Status.COMPLETED, null, null, null, null)));

        verify(milestones, never()).save(any());
    }

    @Test
    void superAdminCanCorrectAPropertySaleWithoutAWorkspaceAssignment() {
        SaleTransaction sale = sale(SaleStatus.LEAD);
        when(users.getUserId()).thenReturn(1L);
        lenient().when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleTransaction updated = service.update(1L,
                new UpdateSaleRequest(SaleStatus.VIEWING, null, "Viewing confirmed"));

        assertEquals(SaleStatus.VIEWING, updated.getStatus());
        verify(properties, never()).findByIdAndManagerRole(anyLong(), anyLong(), anyString());
    }

    @Test void signedOfferAcceptanceIsRetrySafeAfterAutomaticReservation() {
        SaleTransaction sale = sale(SaleStatus.RESERVED);
        when(users.getUserId()).thenReturn(200L); when(users.getActiveRole()).thenReturn(PMSRole.BUYER);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(documents.existsBySaleIdAndDocumentTypeAndStatusAndActiveTrue(eq(1L),any(),any())).thenReturn(true);
        assertSame(sale,service.acceptOffer(1L)); verify(sales,never()).save(any());
    }

    @Test void unsignedOfferCannotBePassedOffAsSignedSaleAgreement() {
        SaleTransaction sale = sale(SaleStatus.AGREEMENT);
        when(users.getUserId()).thenReturn(100L);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L,Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        LeaseDocument draft = new LeaseDocument();draft.setSaleId(1L);draft.setRecipientUserId(200L);
        draft.setStatus(org.pms.silverocean.service.leasedocument.LeaseDocumentStatus.DRAFT);
        draft.setDocumentType(org.pms.silverocean.service.leasedocument.LeaseDocumentType.PROPERTY_SALE_LETTER_OF_OFFER);
        when(documents.findByIdAndPropertyIdAndUnitIdAndActiveTrue(99L,11L,77L)).thenReturn(Optional.of(draft));
        assertThrows(PMSCustomException.class,()->service.addMilestone(1,new SaleMilestoneModels.Create(
                SaleMilestoneModels.Type.AGREEMENT_SIGNED,SaleMilestoneModels.Status.COMPLETED,null,null,99L,null)));
        verify(milestones,never()).save(any());
    }

    @Test void activeEscrowInvoiceMustBeResolvedBeforeSaleCancellation() {
        SaleTransaction sale=sale(SaleStatus.AGREEMENT);sale.setEscrowInvoiceId(99L);
        when(users.getUserId()).thenReturn(100L);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L,Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        PMSInvoice invoice=new PMSInvoice();invoice.setActive(true);
        when(invoices.findById(99L)).thenReturn(Optional.of(invoice));
        assertThrows(PMSCustomException.class,()->service.update(1,new UpdateSaleRequest(SaleStatus.CANCELLED,null,"Buyer withdrawal")));
        assertEquals(SaleStatus.AGREEMENT,sale.getStatus());
    }

    @Test void refundedInvoiceCannotBeHiddenBehindAnEarlierFundedMilestone() {
        SaleTransaction sale = sale(SaleStatus.COMPLETION);
        PMSInvoice invoice = stubSettledEscrow(sale);
        invoice.setPaid(false); invoice.setPendingAmount(invoice.getAmount());
        when(users.getUserId()).thenReturn(100L);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(milestones.existsBySaleIdAndMilestoneTypeAndStatus(eq(1L), anyString(), eq("COMPLETED"))).thenReturn(true);
        assertThrows(PMSCustomException.class, () -> service.update(1L, new UpdateSaleRequest(SaleStatus.COMPLETED, null, null)));
        assertEquals(SaleStatus.COMPLETION, sale.getStatus());
        verifyNoInteractions(estates);
        verify(sales, never()).save(any());
    }

    @Test void paidInvoiceForAnotherPayeeCannotCompleteTheSale() {
        SaleTransaction sale = sale(SaleStatus.COMPLETION);
        PMSInvoice invoice = stubSettledEscrow(sale); invoice.setPayToUserId(999L);
        when(users.getUserId()).thenReturn(100L);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(access.require(11L, Permission.MANAGE_SALE_PIPELINE)).thenReturn(property);
        when(milestones.existsBySaleIdAndMilestoneTypeAndStatus(eq(1L),anyString(),eq("COMPLETED"))).thenReturn(true);
        assertThrows(PMSCustomException.class, () -> service.update(1L,new UpdateSaleRequest(SaleStatus.COMPLETED,null,null)));
        verifyNoInteractions(estates); verify(sales,never()).save(any());
    }

    private PMSInvoice stubSettledEscrow(SaleTransaction sale) {
        sale.setEscrowInvoiceId(501L); sale.setEscrowRequiredAmount(new BigDecimal("1400000"));
        PMSInvoice invoice = new PMSInvoice(); invoice.setId(501L); invoice.setActive(true); invoice.setPaid(true);
        invoice.setBillingType("SALE"); invoice.setPropertyId(11L); invoice.setUnitId(77L); invoice.setBilledUserId(200L);
        invoice.setCurrency("KES"); invoice.setAmount(1400000); invoice.setPendingAmount(0); invoice.setPayToUserId(100L);
        when(invoices.findByIdForUpdate(501L)).thenReturn(Optional.of(invoice));
        return invoice;
    }

    @Test void buyerDirectorySearchRemainsInsideTheSelectedSalesWorkspace() {
        PageRequest page = PageRequest.of(0,25);
        when(users.getUserId()).thenReturn(300L);
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(users.hasPermission(Permission.VIEW_SALE_PIPELINE)).thenReturn(true);
        when(access.selectedAssignmentId()).thenReturn(55L);
        when(sales.findViewPageBySalesScope(300L,false,"SALES_COORDINATOR",55L,"buyer@example.com",page))
                .thenReturn(new PageImpl<>(List.of(),page,0));

        service.list(page,"  buyer@example.com  ");

        verify(sales).findViewPageBySalesScope(300L,false,"SALES_COORDINATOR",55L,"buyer@example.com",page);
    }

    @Test void superAdminDoesNotReceiveAPlatformWideBuyerDirectory() {
        PageRequest page = PageRequest.of(0,25);
        when(users.getUserId()).thenReturn(300L);
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);

        assertEquals(0,service.list(page).getTotalElements());

        verifyNoInteractions(sales);
    }

    private SaleTransaction sale(SaleStatus status) {
        SaleTransaction sale = new SaleTransaction(); sale.setId(1L); sale.setPropertyId(11L); sale.setUnitId(77L);
        sale.setBuyerUserId(200L); sale.setSalesAgentUserId(100L); sale.setStatus(status); sale.setActive(true);
        sale.setCurrency("KES"); sale.setAskingPrice(new BigDecimal("15000000")); return sale;
    }
}
