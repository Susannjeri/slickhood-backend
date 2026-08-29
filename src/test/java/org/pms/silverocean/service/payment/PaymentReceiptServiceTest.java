package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.platforms.mpesa.TransactionCategory;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptServiceTest {
    @Mock PaymentDao payments;
    @Mock InvoiceDao invoices;
    @Mock UserDao users;
    @Mock RenderService renderer;
    PaymentReceiptService service;

    @BeforeEach void setup() {
        service = new PaymentReceiptService(payments, invoices, users, renderer);
        when(users.getUserId()).thenReturn(7L);
        when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(false);
    }

    @Test void rendersVerifiedReceiptForAuthorizedInvoiceParticipant() throws Exception {
        PMSPayment payment = successfulPayment();
        PMSInvoice invoice = invoice();
        Users owner = new Users(); owner.setFullName("Test Landlord");
        when(payments.findPaymentByIdForAuthorizedUser(31L, 7L)).thenReturn(Optional.of(payment));
        when(invoices.getInvoiceByRef("INV-100")).thenReturn(Optional.of(invoice));
        when(users.findById(9L)).thenReturn(Optional.of(owner));
        when(renderer.render(eq("receipt"), any())).thenReturn("<html>receipt</html>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.render(31L, output);

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(renderer).render(eq("receipt"), model.capture());
        verify(renderer).toPdf("<html>receipt</html>", output);
        assertEquals("RCP-31", model.getValue().get("receiptNumber"));
        assertEquals("PAY-ABC", model.getValue().get("providerReference"));
        assertEquals("Test Landlord", model.getValue().get("payee"));
    }

    @Test void refusesReceiptUntilProviderPaymentIsFinal() throws Exception {
        PMSPayment payment = successfulPayment(); payment.setInProgress(true);
        when(payments.findPaymentByIdForAuthorizedUser(31L, 7L)).thenReturn(Optional.of(payment));
        assertThrows(PMSCustomException.class, () -> service.render(31L, new ByteArrayOutputStream()));
        verify(renderer, never()).toPdf(any(), any());
    }

    private PMSPayment successfulPayment() {
        PMSPayment payment = new PMSPayment();
        payment.setId(31L); payment.setBillReference("INV-100"); payment.setAmount(1250d);
        payment.setThirdPartyTransId("PAY-ABC"); payment.setChannel("MPESA");
        payment.setCategory(TransactionCategory.MANUAL_RECORD.name()); payment.setStatus("success");
        payment.setInProgress(false); payment.setCustomerName("Test Tenant");
        payment.setCreatedOn(ZonedDateTime.of(2026, 8, 28, 9, 30, 0, 0, ZoneId.of("Africa/Nairobi")));
        return payment;
    }

    private PMSInvoice invoice() {
        PMSInvoice invoice = new PMSInvoice(); invoice.setRef("INV-100"); invoice.setCurrency("KES");
        invoice.setPayToUserId(9L); invoice.setCustomerEmail("tenant@slickhood.test"); return invoice;
    }
}
