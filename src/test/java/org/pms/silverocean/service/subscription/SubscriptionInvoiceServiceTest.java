package org.pms.silverocean.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.SubscriptionPlan;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionInvoiceServiceTest {
    @Mock private InvoiceDao invoiceDao;
    @Mock private UserDao userDao;
    @Mock private AccountDao accountDao;
    private SubscriptionInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionInvoiceService(invoiceDao, userDao, accountDao, "KES");
    }

    @Test
    void derivesPayeeFromVerifiedSlickHoodAccount() {
        PaymentAccount account = paymentAccount(AccountCategory.SLICKHOOD, true, 99L);
        when(accountDao.getAccountById(12L)).thenReturn(account);
        doAnswer(invocation -> {
            ((PMSInvoice) invocation.getArgument(0)).setRef("INV-SUB-1");
            return null;
        }).when(invoiceDao).createInvoice(any(PMSInvoice.class));

        SubscriptionPendingCheckoutDTO result = service.createSubscriptionCheckout(
                7L, paidPlan(), PMSRole.LANDLORD, 12L);

        ArgumentCaptor<PMSInvoice> invoice = ArgumentCaptor.forClass(PMSInvoice.class);
        verify(invoiceDao).createInvoice(invoice.capture());
        assertEquals(99L, invoice.getValue().getPayToUserId());
        assertEquals(0L, invoice.getValue().getPropertyId());
        assertEquals("INV-SUB-1", result.invoiceRef());
    }

    @Test
    void rejectsLandlordAccountAsSubscriptionPayee() {
        when(accountDao.getAccountById(12L)).thenReturn(paymentAccount(AccountCategory.LANDLORD, true, 99L));

        assertThrows(PMSCustomException.class, () -> service.createSubscriptionCheckout(
                7L, paidPlan(), PMSRole.LANDLORD, 12L));

        verify(invoiceDao, never()).createInvoice(any());
    }

    private static PaymentAccount paymentAccount(AccountCategory category, boolean verified, long ownerId) {
        PaymentAccount account = new PaymentAccount();
        account.setCategory(category);
        account.setVerified(verified);
        account.setCreatedBy(ownerId);
        account.setActive(true);
        return account;
    }

    private static SubscriptionPlan paidPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("GOLD");
        plan.setDisplayName("Gold Plan");
        plan.setPrice(new BigDecimal("7000.00"));
        plan.setCurrency("KES");
        return plan;
    }
}
