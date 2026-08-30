package org.pms.silverocean.service.payment.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.email.EmailService;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.pms.silverocean.service.property.UnitDao;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceSubscriptionPaymentTest {
    @Mock private InvoiceDao invoiceDao;
    @Mock private UnitDao unitDao;
    @Mock private UserDao userDao;
    @Mock private AccountDao accountDao;
    @Mock private RenderService renderService;
    @Mock private EmailService emailService;
    @Mock private I18NService i18NService;
    @Mock private RoleService roleService;
    @Mock private PaymentPlatformFactory paymentPlatformFactory;
    @Mock private PaymentPlatform paymentPlatform;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceDao, unitDao, userDao, accountDao, renderService,
                emailService, i18NService, roleService, paymentPlatformFactory);
    }

    @Test
    void initializesSubscriptionOnlyThroughMatchingVerifiedPlatformAccount() {
        PMSInvoice invoice = subscriptionInvoice(7L, 99L);
        PaymentAccount account = paymentAccount(AccountCategory.SLICKHOOD, PaymentChannel.PAYSTACK, 99L);
        PaymentResponse expected = new PaymentResponse(true, ResponseCode.CARD_PAYMENT_INITIALIZED, "https://pay.example");
        when(invoiceDao.getInvoiceByRef("INV-SUB")).thenReturn(Optional.of(invoice));
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.getAccountById(12L)).thenReturn(account);
        when(paymentPlatformFactory.getPlatform(PaymentChannel.PAYSTACK)).thenReturn(paymentPlatform);
        when(paymentPlatform.processPayment(invoice, null, 12L)).thenReturn(expected);

        PaymentResponse actual = service.initInvoicePayment("INV-SUB", PaymentChannel.PAYSTACK, null, 12L);

        assertSame(expected, actual);
    }

    @Test
    void rejectsSubscriptionInitializationByAnotherSubscriber() {
        PMSInvoice invoice = subscriptionInvoice(7L, 99L);
        when(invoiceDao.getInvoiceByRef("INV-SUB")).thenReturn(Optional.of(invoice));
        when(userDao.getUserId()).thenReturn(8L);

        assertThrows(PaymentRequestException.class,
                () -> service.initInvoicePayment("INV-SUB", PaymentChannel.PAYSTACK, null, 12L));

        verify(accountDao, never()).getAccountById(12L);
        verify(paymentPlatformFactory, never()).getPlatform(PaymentChannel.PAYSTACK);
    }

    @Test
    void rejectsLandlordAccountForSubscriptionInitialization() {
        PMSInvoice invoice = subscriptionInvoice(7L, 99L);
        PaymentAccount landlordAccount = paymentAccount(AccountCategory.LANDLORD, PaymentChannel.PAYSTACK, 99L);
        when(invoiceDao.getInvoiceByRef("INV-SUB")).thenReturn(Optional.of(invoice));
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.getAccountById(12L)).thenReturn(landlordAccount);

        assertThrows(PaymentRequestException.class,
                () -> service.initInvoicePayment("INV-SUB", PaymentChannel.PAYSTACK, null, 12L));

        verify(paymentPlatformFactory, never()).getPlatform(PaymentChannel.PAYSTACK);
    }

    @Test
    void rejectsAccountWhoseChannelDoesNotMatchRequest() {
        PMSInvoice invoice = subscriptionInvoice(7L, 99L);
        PaymentAccount account = paymentAccount(AccountCategory.SLICKHOOD, PaymentChannel.MPESA, 99L);
        when(invoiceDao.getInvoiceByRef("INV-SUB")).thenReturn(Optional.of(invoice));
        when(userDao.getUserId()).thenReturn(7L);
        when(accountDao.getAccountById(12L)).thenReturn(account);

        assertThrows(PaymentRequestException.class,
                () -> service.initInvoicePayment("INV-SUB", PaymentChannel.PAYSTACK, null, 12L));

        verify(paymentPlatformFactory, never()).getPlatform(PaymentChannel.PAYSTACK);
    }

    @Test
    void rejectsPropertyInvoiceInitializationByAUserWhoIsNotBilled() {
        PMSInvoice invoice = subscriptionInvoice(7L, 99L);
        invoice.setSubscriptionPlanCode(null);
        invoice.setBillingType("SERVICE_CHARGE");
        when(invoiceDao.getInvoiceByRef("INV-SUB")).thenReturn(Optional.of(invoice));
        when(userDao.getUserId()).thenReturn(8L);

        assertThrows(PaymentRequestException.class,
                () -> service.initInvoicePayment("INV-SUB", PaymentChannel.PAYSTACK, null, 12L));

        verify(accountDao, never()).getAccountById(12L);
        verify(paymentPlatformFactory, never()).getPlatform(PaymentChannel.PAYSTACK);
    }

    private static PMSInvoice subscriptionInvoice(long billedUserId, long payeeUserId) {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setRef("INV-SUB");
        invoice.setSubscriptionPlanCode("GOLD");
        invoice.setBilledUserId(billedUserId);
        invoice.setPayToUserId(payeeUserId);
        invoice.setActive(true);
        invoice.setPendingAmount(7000);
        return invoice;
    }

    private static PaymentAccount paymentAccount(AccountCategory category, PaymentChannel channel, long ownerId) {
        PaymentAccount account = new PaymentAccount();
        account.setCategory(category);
        account.setChannel(channel);
        account.setCreatedBy(ownerId);
        account.setActive(true);
        account.setVerified(true);
        return account;
    }
}
