package org.pms.silverocean.service.payment.platforms.paystack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.*;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaystackRoutingTest {
    final UserDao users = mock(UserDao.class);
    final PaymentDao payments = mock(PaymentDao.class);
    final AccountDao accounts = mock(AccountDao.class);
    final ParamService params = mock(ParamService.class);
    final RestTemplateService http = mock(RestTemplateService.class);
    final UpdatePaymentService updater = mock(UpdatePaymentService.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PaystackPlatform platform = new PaystackPlatform(updater, users, payments, params, http, mock(EventService.class), mapper, accounts);
    PMSInvoice invoice;
    PaymentAccount account;
    PMSPayment payment;

    @BeforeEach void setup() {
        invoice = new PMSInvoice(); invoice.setPayToUserId(11L); invoice.setBilledUserId(22L);
        invoice.setPendingAmount(100); invoice.setAmount(100); invoice.setCurrency("KES"); invoice.setRef("INV-TEST");
        account = new PaymentAccount(); account.setId(71L); account.setCreatedBy(11L);
        account.setCategory(AccountCategory.SLICKHOOD); account.setChannel(PaymentChannel.PAYSTACK);
        account.setActive(true); account.setVerified(true);
        when(accounts.getAccountById(71L)).thenReturn(account);
        Users customer = new Users(); customer.setId(22L); customer.setEmail("payer@example.test"); customer.setFullName("Test Payer");
        when(users.getUserId()).thenReturn(22L); when(users.findById(22L)).thenReturn(Optional.of(customer));
        doAnswer(call -> { payment = call.getArgument(0); payment.setId(601L); return payment; }).when(payments).savePMSPayment(any());
        doReturn(new PaystackPlatform.PaystackInitializeResponse(true, "Created",
                new PaystackPlatform.PaystackInitializeData("https://checkout.paystack.com/test", "test", "601")))
                .when(http).sendPostRequest(anyString(), any(), any(), eq(PaystackPlatform.PaystackInitializeResponse.class));
        ReflectionTestUtils.setField(platform, "secretKey", "sk_test_fixture");
        ReflectionTestUtils.setField(platform, "apiUrl", "https://api.paystack.co");
        ReflectionTestUtils.setField(platform, "callbackUrl", "https://app.slickhood.com/payment/callback");
        ReflectionTestUtils.setField(platform, "configuredChannels", "card,mobile_money");
        ReflectionTestUtils.setField(platform, "feeBearer", "subaccount");
    }

    @Test void slickhoodSubscriptionUsesOnlyMainIntegration() throws Exception {
        invoice.setSubscriptionPlanCode("BRONZE");
        assertTrue(platform.processPayment(invoice, null, 71L).success());
        var request = org.mockito.ArgumentCaptor.forClass(PaystackPlatform.PaystackInitializeRequest.class);
        verify(http).sendPostRequest(anyString(), request.capture(), any(), any());
        assertNull(request.getValue().subaccount()); assertNull(request.getValue().bearer());
        assertFalse(mapper.writeValueAsString(request.getValue()).contains("subaccount"));
        verifyNoInteractions(params);
    }

    @ParameterizedTest @EnumSource(value=AccountCategory.class, names={"LANDLORD","ESTATE_MANAGEMENT","PROPERTY_SALES","MERCHANT"})
    void independentPayeeAlwaysUsesItsSubaccount(AccountCategory category) {
        account.setCategory(category); invoice.setPropertyId(44L);
        when(params.getParamByAccountIdAndType(eq(71L), any(), eq(44L))).thenReturn("ACCT_test_recipient");
        platform.processPayment(invoice, null, 71L);
        var request = org.mockito.ArgumentCaptor.forClass(PaystackPlatform.PaystackInitializeRequest.class);
        verify(http).sendPostRequest(anyString(), request.capture(), any(), any());
        assertEquals("ACCT_test_recipient", request.getValue().subaccount());
    }

    @Test void missingRecipientNeverFallsBackToSlickhood() {
        account.setCategory(AccountCategory.LANDLORD);
        when(params.getParamByAccountIdAndType(anyLong(), any(), anyLong())).thenReturn(" ");
        assertThrows(PaymentRequestException.class, () -> platform.processPayment(invoice, null, 71L));
        verifyNoInteractions(http, payments); assertFalse(invoice.isTransactionInProgress());
    }

    @Test void nonSubscriptionCannotUsePlatformAccount() {
        assertThrows(PaymentRequestException.class, () -> platform.processPayment(invoice, null, 71L));
        verifyNoInteractions(http, payments, params);
    }

    @Test void subscriptionCannotUseAnotherPayeesAccount() {
        invoice.setSubscriptionPlanCode("BRONZE"); account.setCreatedBy(99L);
        assertThrows(PaymentRequestException.class, () -> platform.processPayment(invoice, null, 71L));
        verifyNoInteractions(http, payments, params);
    }

    @Test void providerDomainMustMatchTheConfiguredTestKey() {
        invoice.setSubscriptionPlanCode("BRONZE"); platform.processPayment(invoice, null, 71L);
        when(payments.findPaymentByID(601L)).thenReturn(Optional.of(payment));
        when(updater.getInvoicePayToIDUsingInvoiceRef("INV-TEST")).thenReturn(Optional.of(invoice));
        doReturn(new PaystackPlatform.PaystackVerifyResponse(true, "Verified",
                new PaystackPlatform.PaystackTransaction(123L, "success", "601", 10000L, "KES", "Approved", "live")))
                .when(http).sendGetRequest(anyString(), any(), eq(PaystackPlatform.PaystackVerifyResponse.class));
        platform.handleCallBack(new PaystackCallbackDTO("{\"event\":\"charge.success\",\"data\":{\"reference\":\"601\"}}", "127.0.0.1"));
        verify(updater, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), anyDouble());
        assertEquals("verification_failed", payment.getStatus());
    }
}
