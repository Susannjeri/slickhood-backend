package org.pms.silverocean.service.payment.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.email.EmailService;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.property.wrappers.PropertyNameAddressAndTypeProjection;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceAccessBoundaryTest {
    @Mock InvoiceDao invoices;
    @Mock UnitDao units;
    @Mock UserDao users;
    @Mock AccountDao accounts;
    @Mock RenderService renderer;
    @Mock EmailService email;
    @Mock I18NService i18n;
    @Mock PaymentPlatformFactory platforms;
    @Mock org.pms.silverocean.service.architecture.events.DomainEventOutboxPublisher events;

    InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoices, units, users, accounts, renderer, email, i18n, platforms, events);
    }

    @Test
    void tenantListUsesExactInvoiceParticipantScopeAndDoesNotDependOnCurrentTenancyJoin() {
        PMSInvoice invoice = rentalInvoice(185L, 175L);
        Unit unit = new Unit();
        unit.setId(512L); unit.setRef("GT009"); unit.setPropertyId(78L);
        Users tenant = new Users();
        tenant.setId(185L); tenant.setFullName("Tenant Customer");
        PropertyNameAddressAndTypeProjection property = mock(PropertyNameAddressAndTypeProjection.class);

        when(users.getUserId()).thenReturn(185L);
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        when(invoices.getInvoicesForOwnerAndTenantView(any(), eq(185L), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(invoice)));
        when(units.getPropertyDetailsFromUnitId(512L)).thenReturn(Optional.of(property));
        when(property.getName()).thenReturn("GTC Centre");
        when(units.findById(512L)).thenReturn(Optional.of(unit));
        when(users.findById(185L)).thenReturn(Optional.of(tenant));
        when(users.findById(175L)).thenReturn(Optional.of(new Users()));

        var result = service.getInvoiceList(PageRequest.of(0, 10), null, null, null, null);

        assertEquals(1, result.getTotalElements());
        assertEquals("GT009", result.getContent().getFirst().unitRef());
        assertEquals("Tenant Customer", result.getContent().getFirst().tenantName());
        verify(units, never()).getTenantAndUnitDetailsByUnitId(anyLong(), anyLong());
        verify(invoices, never()).getPlatformInvoices(any(), any());
    }

    @Test
    void superAdminListIsRestrictedToSlickHoodSubscriptionInvoices() {
        when(users.getUserId()).thenReturn(159L);
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        when(invoices.getPlatformInvoices(any(), isNull())).thenReturn(new PageImpl<>(List.of()));

        service.getInvoiceList(PageRequest.of(0, 10), null, null, null, null);

        verify(invoices).getPlatformInvoices(any(), isNull());
        verify(invoices, never()).getInvoicesForOwnerAndTenantView(any(), anyLong(), any(), any());
    }

    @Test
    void superAdminCannotOpenCustomerRentalInvoicePdf() {
        when(users.getUserId()).thenReturn(159L);
        when(users.getActiveRole()).thenReturn(PMSRole.SUPER_ADMIN);
        when(invoices.getPlatformInvoice(3541L)).thenReturn(Optional.empty());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.viewInvoicePDF(3541L, new ByteArrayOutputStream()));

        assertEquals(ResponseCode.INVALID_INVOICE_NUMBER, error.getResponseCode());
        verify(invoices, never()).getInvoiceById(anyLong());
    }

    @Test
    void superAdminCannotInitiatePaymentForCustomerInvoiceThroughGlobalLookup() {
        when(users.getUserId()).thenReturn(159L);
        when(invoices.getInvoiceForOwnerOrTenantView("INV-DD5", 159L)).thenReturn(Optional.empty());

        assertThrows(PaymentRequestException.class,
                () -> service.initInvoicePayment("INV-DD5", PaymentChannel.MPESA, null, 1L));

        verify(invoices, never()).getInvoiceByRef("INV-DD5");
    }

    @Test
    void tenantWorkspaceStillShowsTenantInvoicesWhenAccountAlsoPossessesSuperAdminRole() {
        PMSInvoice invoice = rentalInvoice(185L, 175L);
        Unit unit = new Unit();
        unit.setId(512L); unit.setRef("GT009"); unit.setPropertyId(78L);
        Users tenant = new Users();
        tenant.setId(185L); tenant.setFullName("Tenant Customer");
        PropertyNameAddressAndTypeProjection property = mock(PropertyNameAddressAndTypeProjection.class);

        when(users.getUserId()).thenReturn(185L);
        when(users.getActiveRole()).thenReturn(PMSRole.TENANT);
        when(invoices.getInvoicesForOwnerAndTenantView(any(), eq(185L), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(invoice)));
        when(units.getPropertyDetailsFromUnitId(512L)).thenReturn(Optional.of(property));
        when(property.getName()).thenReturn("GTC Centre");
        when(units.findById(512L)).thenReturn(Optional.of(unit));
        when(users.findById(185L)).thenReturn(Optional.of(tenant));
        when(users.findById(175L)).thenReturn(Optional.of(new Users()));

        var result = service.getInvoiceList(PageRequest.of(0, 10), null, null, null, null);

        assertEquals(1, result.getTotalElements());
        verify(invoices).getInvoicesForOwnerAndTenantView(any(), eq(185L), isNull(), isNull());
        verify(invoices, never()).getPlatformInvoices(any(), any());
    }

    @Test
    void exactInvoiceDeepLinkStillUsesParticipantScope() {
        PageRequest page = PageRequest.of(0, 10);
        when(users.getUserId()).thenReturn(185L);
        when(users.getActiveRole()).thenReturn(PMSRole.HOMEOWNER);
        when(invoices.getInvoicesForOwnerAndTenantView(page, 185L, null, null, 501L))
                .thenReturn(org.springframework.data.domain.Page.empty(page));

        service.getInvoiceList(page, null, null, null, null, 501L);

        verify(invoices).getInvoicesForOwnerAndTenantView(page, 185L, null, null, 501L);
        verify(invoices, never()).getInvoiceById(501L);
    }

    @Test
    void lateFeeInvoicePreservesTheOriginalPaymentAndWorkspaceBoundary() {
        PMSInvoice source = rentalInvoice(185L, 175L);
        source.setPaymentAccountId(91L);
        Unit unit = new Unit();
        unit.setId(512L);
        unit.setRef("GT009");
        unit.setPropertyId(78L);
        unit.setCurrency("KES");
        when(units.findById(512L)).thenReturn(Optional.of(unit));
        doAnswer(invocation -> {
            PMSInvoice saved = invocation.getArgument(0);
            saved.setId(3542L);
            saved.setRef("INV-DD6");
            return null;
        }).when(invoices).createInvoice(any(PMSInvoice.class));

        LocalDate assessedOn = LocalDate.of(2026, 9, 13);
        PMSInvoice result = service.createLateFeeInvoice(source, new BigDecimal("750.00"),
                new BigDecimal("2.50"), assessedOn);

        ArgumentCaptor<PMSInvoice> persisted = ArgumentCaptor.forClass(PMSInvoice.class);
        verify(invoices).createInvoice(persisted.capture());
        PMSInvoice fee = persisted.getValue();
        assertEquals(185L, fee.getBilledUserId());
        assertEquals(175L, fee.getPayToUserId());
        assertEquals(91L, fee.getPaymentAccountId());
        assertEquals(512L, fee.getUnitId());
        assertEquals(78L, fee.getPropertyId());
        assertEquals("RENTAL", fee.getBillingType());
        assertEquals("KES", fee.getCurrency());
        assertEquals(750.00, fee.getAmount());
        assertEquals(750.00, fee.getPendingAmount());
        assertEquals(assessedOn, fee.getDueDate());
        assertEquals(3541L, fee.getLateFeeSourceInvoiceId());
        assertEquals(0, new BigDecimal("2.50").compareTo(fee.getLateFeePercentageRate()));
        assertEquals(fee, result);
    }

    private static PMSInvoice rentalInvoice(long billedUserId, long payToUserId) {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(3541L);
        invoice.setRef("INV-DD5");
        invoice.setUnitId(512L);
        invoice.setPropertyId(78L);
        invoice.setBillingType("RENTAL");
        invoice.setBilledUserId(billedUserId);
        invoice.setPayToUserId(payToUserId);
        invoice.setAmount(30_000);
        invoice.setPendingAmount(30_000);
        invoice.setCurrency("KES");
        invoice.setActive(true);
        invoice.setCreatedOn(ZonedDateTime.now());
        return invoice;
    }
}
