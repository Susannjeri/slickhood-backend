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
    SalesService service;
    Property property;
    Unit unit;
    Users buyer;

    @BeforeEach
    void setUp() {
        service = new SalesService(sales, properties, units, users, estates, milestones, invites, notifications, i18n, documents);
        property = new Property(); property.setId(11L); property.setActive(true); property.setCreatedBy(100L);
        property.setManagementMode(PMSPropertyManagementMode.SALE);
        unit = new Unit(); unit.setId(77L); unit.setPropertyId(11L); unit.setActive(true); unit.setLeaseMode("SALE"); unit.setCurrency("KES");
        buyer = new Users(); buyer.setId(200L); buyer.setActive(true); buyer.setEmail("buyer@example.com");
    }

    @Test
    void salesWorkspaceOwnerCreatesOnlySaleModeUnitTransactions() {
        when(users.getUserId()).thenReturn(100L);
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 100L)).thenReturn(Optional.of(property));
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
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 100L)).thenReturn(Optional.of(property));
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
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);
        when(properties.findByIdAndCreatedByAndActiveTrue(11L, 100L)).thenReturn(Optional.of(property));
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
        PageRequest bounded = PageRequest.of(0, 100);
        when(users.getUserId()).thenReturn(300L);
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(users.hasPermission(Permission.VIEW_SALE_PIPELINE)).thenReturn(true);
        when(sales.findViewPageByPropertyAccess(300L, bounded)).thenReturn(new PageImpl<>(List.of(), bounded, 0));

        service.list(PageRequest.of(0, 500));

        verify(sales).findViewPageByPropertyAccess(300L, bounded);
    }

    @Test
    void delegatedSalesEmployeeCanRecordOfferButCannotAcceptForBuyer() {
        SaleTransaction sale = sale(SaleStatus.LEAD);
        when(users.getUserId()).thenReturn(300L);
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(properties.findByIdAndManagerRole(11L, 300L, PMSRole.SALES_COORDINATOR.name())).thenReturn(Optional.of(property));
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
        when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(properties.findByIdAndManagerRole(11L, 300L, PMSRole.SALES_COORDINATOR.name())).thenReturn(Optional.of(property));
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
        when(users.getUserId()).thenReturn(300L);
        when(users.getActiveRole()).thenReturn(PMSRole.LISTING_AGENT);
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(properties.findByIdAndManagerRole(11L, 300L, PMSRole.LISTING_AGENT.name())).thenReturn(Optional.of(property));
        when(milestones.existsBySaleIdAndMilestoneTypeAndStatus(eq(1L), anyString(), eq("COMPLETED"))).thenReturn(true);
        when(sales.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SaleTransaction completed = service.update(1L, new UpdateSaleRequest(SaleStatus.COMPLETED, null, null));

        assertEquals(SaleStatus.COMPLETED, completed.getStatus());
        verify(estates).transferFromSale(11L, 77L, 200L, 1L);
    }

    private SaleTransaction sale(SaleStatus status) {
        SaleTransaction sale = new SaleTransaction(); sale.setId(1L); sale.setPropertyId(11L); sale.setUnitId(77L);
        sale.setBuyerUserId(200L); sale.setSalesAgentUserId(100L); sale.setStatus(status); sale.setActive(true);
        sale.setCurrency("KES"); sale.setAskingPrice(new BigDecimal("15000000")); return sale;
    }
}
