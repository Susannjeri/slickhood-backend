package org.pms.silverocean.service.payment.platforms.mpesa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.RestTemplateService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentResponseDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.Body;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.CallbackItem;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.CallbackMetadata;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.STKCallback;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.STKCallbackResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MPesaServiceDestinationSecurityTest {
    @Mock private RestTemplateService restTemplateService;
    @Mock private ConfigService configService;
    @Mock private ParamService paramService;
    @Mock private EventService eventService;
    @Mock private UserDao userDao;
    @Mock private PaymentDao paymentDao;
    @Mock private AccountDao accountDao;
    @Mock private UpdatePaymentService updatePaymentService;

    private MPesaService service;

    @BeforeEach
    void setUp() {
        service = new MPesaService(restTemplateService, configService, paramService, eventService,
                userDao, paymentDao, accountDao, updatePaymentService, new ObjectMapper());
    }

    @Test
    void confirmationCannotCreditInvoiceWhenShortcodeDoesNotMatchFixedAccount() {
        PMSInvoice invoice = invoice();
        PaymentAccount account = account(PaymentChannel.MPESA);
        when(paymentDao.callbackAlreadyProcessed(any(), any(), any(), any())).thenReturn(false);
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-1")).thenReturn(Optional.of(invoice));
        when(accountDao.getAccountById(91L)).thenReturn(account);
        when(paramService.getParamByAccountIdAndType(eq(91L),
                eq(PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.PAYBILL)), eq(44L)))
                .thenReturn("123456");

        service.confirmPayment(callback("999999"), 77L, "127.0.0.1");

        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), any(), any(Double.class));
        ArgumentCaptor<PMSPayment> saved = ArgumentCaptor.forClass(PMSPayment.class);
        verify(paymentDao).savePMSPayment(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getCode());
    }

    @Test
    void validationAcceptsOnlyTheVerifiedDestinationFixedOnInvoice() {
        PMSInvoice invoice = invoice();
        PaymentAccount account = account(PaymentChannel.MPESA_BANK);
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-1")).thenReturn(Optional.of(invoice));
        when(accountDao.getAccountById(91L)).thenReturn(account);
        when(paramService.getParamByAccountIdAndType(eq(91L),
                eq(PaymentChannel.MPESA_BANK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT)), eq(44L)))
                .thenReturn("0011223344");

        MPesaPaymentResponseDTO result = service.validatePayment(callback("0011223344"), 77L, "127.0.0.1");

        assertThat(result.resultCode()).isEqualTo(MPesaResultCodes.VALID.getCode());
        assertThat(invoice.isTransactionInProgress()).isTrue();
        verify(updatePaymentService).updateInvoice(invoice);
    }

    @Test
    void validationRejectsCallbackWhenAccountOwnerIsNotInvoicePayee() {
        PMSInvoice invoice = invoice();
        PaymentAccount account = account(PaymentChannel.MPESA);
        account.setCreatedBy(88L);
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-1")).thenReturn(Optional.of(invoice));
        when(accountDao.getAccountById(91L)).thenReturn(account);

        MPesaPaymentResponseDTO result = service.validatePayment(callback("123456"), 77L, "127.0.0.1");

        assertThat(result.resultCode()).isEqualTo(MPesaResultCodes.INVALID_ACCOUNT_NUMBER.getCode());
        verify(updatePaymentService, never()).updateInvoice(any());
    }

    @Test
    void failedStkCallbackCannotPayEvenWhenItContainsAReceipt() {
        PMSPayment payment = stkPayment();
        when(paymentDao.findPaymentByThirdPartyID("CHECKOUT-1")).thenReturn(Optional.of(payment));

        service.stkCallBack(stkCallback(1032, "RCPT-FAIL", "500.00"), "127.0.0.1");

        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), any(), any(Double.class));
        assertThat(payment.getProviderReceipt()).isNull();
    }

    @Test
    void successfulStkCallbackRequiresTheExactAmountAndVerifiedFixedDestination() {
        PMSPayment payment = stkPayment();
        PMSInvoice invoice = invoice();
        when(paymentDao.findPaymentByThirdPartyID("CHECKOUT-1")).thenReturn(Optional.of(payment));
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-1")).thenReturn(Optional.of(invoice));
        when(paymentDao.providerReceiptAlreadyProcessed(PaymentChannel.MPESA.getName(), "RCPT-1", 101L))
                .thenReturn(false);

        service.stkCallBack(stkCallback(0, "RCPT-1", "499.00"), "127.0.0.1");

        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), any(), any(Double.class));
        assertThat(payment.getProviderReceipt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(MPesaResultCodes.INVALID_AMOUNT.getCode());
    }

    @Test
    void successfulStkCallbackSettlesOnceWithProviderAmountAndReceipt() {
        PMSPayment payment = stkPayment();
        PMSInvoice invoice = invoice();
        PaymentAccount account = account(PaymentChannel.MPESA);
        when(paymentDao.findPaymentByThirdPartyID("CHECKOUT-1")).thenReturn(Optional.of(payment));
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-1")).thenReturn(Optional.of(invoice));
        when(accountDao.getAccountById(91L)).thenReturn(account);
        when(paramService.getParamByAccountIdAndType(eq(91L),
                eq(PaymentChannel.MPESA.findProperty(PaymentPropertyKeys.PAYBILL)), eq(44L)))
                .thenReturn("123456");
        when(paymentDao.providerReceiptAlreadyProcessed(PaymentChannel.MPESA.getName(), "RCPT-1", 101L))
                .thenReturn(false);

        service.stkCallBack(stkCallback(0, "RCPT-1", "500.00"), "127.0.0.1");

        verify(updatePaymentService).setInvoiceToPaid(invoice, "RCPT-1", 500D);
        assertThat(payment.getProviderReceipt()).isEqualTo("RCPT-1");
    }

    private PMSInvoice invoice() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(5L);
        invoice.setRef("INV-1");
        invoice.setPropertyId(44L);
        invoice.setPaymentAccountId(91L);
        invoice.setPayToUserId(77L);
        invoice.setPendingAmount(500D);
        invoice.setActive(true);
        return invoice;
    }

    private PaymentAccount account(PaymentChannel channel) {
        PaymentAccount account = new PaymentAccount();
        account.setId(91L);
        account.setChannel(channel);
        account.setCreatedBy(77L);
        account.setActive(true);
        account.setVerified(true);
        return account;
    }

    private MPesaPaymentDTO callback(String destination) {
        return new MPesaPaymentDTO("Pay Bill", "MPESA-1", "20260905110000", "300.00",
                destination, "INV-1", null, null, null, "254700000000", "Mama", "Njeri", null);
    }

    private PMSPayment stkPayment() {
        PMSPayment payment = new PMSPayment();
        payment.setId(101L);
        payment.setThirdPartyTransId("CHECKOUT-1");
        payment.setBillReference("INV-1");
        payment.setAmount(500D);
        payment.setChannel(PaymentChannel.MPESA.getName());
        payment.setCategory(TransactionCategory.STK.name());
        payment.setAccountId(91L);
        payment.setReceivingAccountNumber("123456");
        payment.setInProgress(true);
        return payment;
    }

    private STKCallbackResponse stkCallback(int resultCode, String receipt, String amount) {
        CallbackMetadata metadata = new CallbackMetadata(List.of(
                new CallbackItem("Amount", amount),
                new CallbackItem("MpesaReceiptNumber", receipt)));
        return new STKCallbackResponse(new Body(new STKCallback(
                "MERCHANT-1", "CHECKOUT-1", resultCode, "result", metadata)));
    }
}
