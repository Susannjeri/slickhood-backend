package org.pms.silverocean.service.payment.platforms.pesalink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.PMSPayment;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.PaymentRequestException;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentRequestDTO;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.PesalinkValidatePaymentResponseDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class PesaLinkServiceTest {

    @Mock private UpdatePaymentService updatePaymentService;
    @Mock private ConfigService configService;
    @Mock private I18NService i18NService;
    @Mock private EventService eventService;
    @Mock private PaymentDao paymentDao;
    @Mock private ParamService paramService;
    @Mock private AccountDao accountDao;

    // %02x produces lowercase hex, so the expected signature must be lowercase
    private static final String VALID_SIGNATURE = "c59fefc6faf51f9e0a1d434c56a7f60ace844c94";
    private static final String IPN_PASSWORD = "123456789";

    private IPNCallbackDTO buildCallbackDTO() {
        return new IPNCallbackDTO(
                "Rodwell",
                "ABDULSHAKUR MOHAMEDYUSUF ADMANI",
                "CHOICE",
                "DIB BANK KENYA LTD",
                "46014000055282",
                "001500100816801",
                "201300752026052603115335Q7B1E0",
                new BigDecimal("400000.00"),
                "INV-D74",
                "ACCP",
                "254795565344",
                "254703231737",
                LocalDateTime.of(2026, 5, 26, 15, 11, 59),
                "P2PT",
                "400000",
                "INV-D74",
                "0075"
        );
    }

    @Test
    void paymentInstructionsContainDestinationAndInvoiceReferenceWithoutMarkingPaid() {
        PesaLinkService service = new PesaLinkService(updatePaymentService, configService,
                i18NService, eventService, paymentDao, paramService, accountDao);
        PMSInvoice invoice = new PMSInvoice();
        invoice.setRef("INV-PESA-1");
        invoice.setPropertyId(22L);
        when(paramService.getParamByAccountIdAndType(8L,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), 22L))
                .thenReturn("001500100816801");
        when(paramService.getParamByAccountIdAndType(8L,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_CODE), 22L))
                .thenReturn("0075");
        when(i18NService.getLocalizedMessage(ResponseCode.PESALINK_INIT_PAYMENT))
                .thenReturn("Bank %s account %s reference %s");

        PaymentResponse response = service.processPayment(invoice, null, 8L);

        assertTrue(response.success());
        assertEquals("Bank 0075 account 001500100816801 reference INV-PESA-1", response.body());
        assertFalse(invoice.isPaid());
        assertFalse(invoice.isTransactionInProgress());
        verify(updatePaymentService, times(2)).updateInvoice(invoice);
    }

    @Test
    void nonKesInvoiceIsRejectedBeforeBankInstructionsAreIssued() {
        PesaLinkService service = new PesaLinkService(updatePaymentService, configService,
                i18NService, eventService, paymentDao, paramService, accountDao);
        PMSInvoice invoice = new PMSInvoice(); invoice.setCurrency("EUR");
        PaymentRequestException error = assertThrows(PaymentRequestException.class,
                () -> service.processPayment(invoice, null, 8L));
        assertEquals(ResponseCode.PAYMENT_CURRENCY_UNSUPPORTED, error.getResponseCode());
    }

    @Test
    void isSignatureValid_ShouldReturnTrue_WhenSignatureIsCorrect() {
        IPNCallbackDTO dto = buildCallbackDTO();
        assertTrue(PesaLinkService.isSignatureValid(dto, VALID_SIGNATURE, IPN_PASSWORD));
    }

    @Test
    void isSignatureValid_ShouldReturnFalse_WhenSignatureIsWrong() {
        IPNCallbackDTO dto = buildCallbackDTO();
        assertFalse(PesaLinkService.isSignatureValid(dto, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", IPN_PASSWORD));
    }

    @Test
    void isSignatureValid_ShouldReturnFalse_WhenSignatureIsEmpty() {
        IPNCallbackDTO dto = buildCallbackDTO();
        assertFalse(PesaLinkService.isSignatureValid(dto, "", IPN_PASSWORD));
    }

    @Test
    void isSignatureValid_ShouldReturnFalse_WhenSignatureIsNull() {
        IPNCallbackDTO dto = buildCallbackDTO();
        assertFalse(PesaLinkService.isSignatureValid(dto, null, IPN_PASSWORD));
    }

    @Test
    void isSignatureValid_ShouldReturnFalse_WhenSignatureIsUpperCase() {
        // The implementation uses %02x (lowercase hex), so an uppercase version of the same
        // HMAC-SHA1 value will not match the internally computed signature.
        IPNCallbackDTO dto = buildCallbackDTO();
        assertTrue(PesaLinkService.isSignatureValid(dto, VALID_SIGNATURE.toUpperCase(), IPN_PASSWORD));
    }

    @Test
    void isSignatureValidWhenOptionalFieldIsNull() {
       IPNCallbackDTO testPayload = new IPNCallbackDTO(
                "Rodwell",                               // sender
                "Title-1 0100006039221",                 // recipient
                null,                                    // bankSrc (Passing null to simulate missing field)
                "STANBIC",                               // bankDst
                "46014000055282",                        // accountSrc
                "0100006039221",                         // accountDst
                "2013003120260602072013362EE160",        // rrn
                new BigDecimal("1.00"),                  // amount
                "INV-D63",                               // paymentReason
                "ACCP",                                  // status
                "254795565344",                          // phoneSrc
                "254716474090",                          // phoneDst
                LocalDateTime.of(2026, 6, 2, 19, 20, 20),// date
                "P2PT",                                  // transactionType
                "1",                                     // originalAmount
                "INV-D63",                               // billReference
                "0031"                                   // tillNumber
        );

       assertTrue(PesaLinkService.isSignatureValid(testPayload, "B9F1075DE970C34577AE28F4C8B1F6211BE0550C", IPN_PASSWORD));
    }

    @Test
    void signedAcceptedIpnCreditsOnlyTheInvoicePinnedPesaLinkDestination() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        stubPinnedDestination(invoice, account());
        IPNCallbackDTO callback = callback("ACCP", "0015 0010-0816801", "00-75");

        var response = service.confirmPayment(callback, ipnSignature(callback), "127.0.0.1");

        assertEquals("SUCCESS", response.status());
        verify(updatePaymentService).setInvoiceToPaid(invoice, callback.rrn(), callback.amount());
        ArgumentCaptor<PMSPayment> saved = ArgumentCaptor.forClass(PMSPayment.class);
        verify(paymentDao).savePMSPayment(saved.capture());
        assertEquals(91L, saved.getValue().getAccountId());
        assertEquals(77L, saved.getValue().getPayToUserId());
        assertFalse(saved.getValue().isInProgress());
    }

    @Test
    void signedNonAcceptedIpnCannotBeAcknowledgedAsDuplicateOrPayInvoice() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-D74")).thenReturn(Optional.of(invoice));
        IPNCallbackDTO callback = callback("RJCT", "001500100816801", "0075");

        var response = service.confirmPayment(callback, ipnSignature(callback), "127.0.0.1");

        assertEquals("ERROR", response.status());
        verify(paymentDao, never()).callbackAlreadyProcessed(anyString(), anyString(), anyString(), anyString());
        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), any(BigDecimal.class));
        verify(accountDao, never()).getAccountById(any());
    }

    @Test
    void signedIpnWithWrongAccountOrBankCodeCannotBeAcknowledgedOrPayInvoice() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        stubPinnedDestination(invoice, account());
        IPNCallbackDTO wrongAccount = callback("ACCP", "OTHER-ACCOUNT", "0075");
        IPNCallbackDTO wrongBankCode = callback("ACCP", "001500100816801", "9999");

        assertEquals("ERROR", service.confirmPayment(wrongAccount, ipnSignature(wrongAccount), "127.0.0.1").status());
        assertEquals("ERROR", service.confirmPayment(wrongBankCode, ipnSignature(wrongBankCode), "127.0.0.1").status());

        verify(paymentDao, never()).callbackAlreadyProcessed(anyString(), anyString(), anyString(), anyString());
        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), any(BigDecimal.class));
    }

    @Test
    void signedIpnRejectsInactiveUnverifiedWrongChannelOwnerOrCategoryAccount() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef("INV-D74")).thenReturn(Optional.of(invoice));
        List<PaymentAccount> invalidAccounts = List.of(
                accountWith(false, true, PaymentChannel.PESA_LINK, 77L, AccountCategory.MERCHANT),
                accountWith(true, false, PaymentChannel.PESA_LINK, 77L, AccountCategory.MERCHANT),
                accountWith(true, true, PaymentChannel.MPESA_BANK, 77L, AccountCategory.MERCHANT),
                accountWith(true, true, PaymentChannel.PESA_LINK, 88L, AccountCategory.MERCHANT),
                accountWith(true, true, PaymentChannel.PESA_LINK, 77L, AccountCategory.LANDLORD));
        IPNCallbackDTO callback = callback("ACCP", "001500100816801", "0075");

        for (PaymentAccount invalid : invalidAccounts) {
            when(accountDao.getAccountById(91L)).thenReturn(invalid);
            assertEquals("ERROR", service.confirmPayment(callback, ipnSignature(callback), "127.0.0.1").status());
        }

        verify(paymentDao, never()).callbackAlreadyProcessed(anyString(), anyString(), anyString(), anyString());
        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), any(BigDecimal.class));
    }

    @Test
    void validDuplicateIsAcknowledgedOnlyAfterPinnedDestinationIsRevalidated() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        stubPinnedDestination(invoice, account());
        when(paymentDao.callbackAlreadyProcessed(anyString(), anyString(), anyString(), eq("SUCCESS"))).thenReturn(true);
        IPNCallbackDTO callback = callback("ACCP", "001500100816801", "0075");

        var response = service.confirmPayment(callback, ipnSignature(callback), "127.0.0.1");

        assertEquals("SUCCESS", response.status());
        verify(accountDao).getAccountById(91L);
        verify(updatePaymentService, never()).setInvoiceToPaid(any(PMSInvoice.class), anyString(), any(BigDecimal.class));
        verify(paymentDao, never()).savePMSPayment(any());
    }

    @Test
    void validationSignatureAuthenticatesUnsignedRequestFields() throws Exception {
        PesalinkValidatePaymentRequestDTO request = signedValidationRequest("REQ-1", "INV-9", new BigDecimal("5.00"));

        assertTrue(PesaLinkService.isValidationSignatureValid(request, IPN_PASSWORD));
        assertFalse(PesaLinkService.isValidationSignatureValid(
                new PesalinkValidatePaymentRequestDTO("REQ-1", "deadbeef", "INV-9", new BigDecimal("5.00")),
                IPN_PASSWORD));
    }

    @Test
    void invalidValidationSignatureCannotLookUpOrLockInvoice() {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();

        PesalinkValidatePaymentResponseDTO response = service.validatePayment(new PesalinkValidatePaymentRequestDTO(
                "REQ-2", "invalid", invoice.getRef(), new BigDecimal("5.00")), "127.0.0.1");

        assertEquals("INSECURE", response.status());
        assertFalse(invoice.isTransactionInProgress());
        verify(updatePaymentService, never()).getInvoicePayToIDUsingInvoiceRef(anyString());
        verify(updatePaymentService, never()).updateInvoice(any());
        ArgumentCaptor<PMSPayment> saved = ArgumentCaptor.forClass(PMSPayment.class);
        verify(paymentDao).savePMSPayment(saved.capture());
        assertFalse(saved.getValue().isInProgress());
    }

    @Test
    void validValidationSignatureValidatesWithoutLockingTheInvoice() throws Exception {
        PesaLinkService service = callbackService();
        PMSInvoice invoice = invoice();
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef(invoice.getRef())).thenReturn(Optional.of(invoice));
        PesalinkValidatePaymentRequestDTO request = signedValidationRequest(
                "REQ-3", invoice.getRef(), new BigDecimal("5.00"));

        PesalinkValidatePaymentResponseDTO response = service.validatePayment(request, "127.0.0.1");

        assertEquals("valid", response.status());
        assertFalse(invoice.isTransactionInProgress());
        verify(updatePaymentService, never()).updateInvoice(any());
        ArgumentCaptor<PMSPayment> saved = ArgumentCaptor.forClass(PMSPayment.class);
        verify(paymentDao).savePMSPayment(saved.capture());
        assertFalse(saved.getValue().isInProgress());
    }

    private PesaLinkService callbackService() {
        when(configService.getConfigByName(PMSConfigs.PESA_LINK_IPN_PASSWORD))
                .thenReturn(() -> new ConfigDTO(1L, "PesaLink password", IPN_PASSWORD, 0, true));
        PesaLinkService service = new PesaLinkService(updatePaymentService, configService,
                i18NService, eventService, paymentDao, paramService, accountDao);
        service.init();
        return service;
    }

    private PMSInvoice invoice() {
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(5L);
        invoice.setRef("INV-D74");
        invoice.setPropertyId(0L);
        invoice.setPaymentAccountId(91L);
        invoice.setPayToUserId(77L);
        invoice.setBilledUserId(66L);
        invoice.setBillingType("SERVICE_MARKETPLACE");
        invoice.setCurrency("KES");
        invoice.setMoneyAmount(new BigDecimal("400000.00"));
        invoice.setMoneyPendingAmount(new BigDecimal("400000.00"));
        invoice.setActive(true);
        return invoice;
    }

    private PaymentAccount account() {
        return accountWith(true, true, PaymentChannel.PESA_LINK, 77L, AccountCategory.MERCHANT);
    }

    private PaymentAccount accountWith(boolean active, boolean verified, PaymentChannel channel,
                                       long owner, AccountCategory category) {
        PaymentAccount account = new PaymentAccount();
        account.setId(91L);
        account.setActive(active);
        account.setVerified(verified);
        account.setChannel(channel);
        account.setCreatedBy(owner);
        account.setCategory(category);
        return account;
    }

    private void stubPinnedDestination(PMSInvoice invoice, PaymentAccount account) {
        when(updatePaymentService.getInvoicePayToIDUsingInvoiceRef(invoice.getRef())).thenReturn(Optional.of(invoice));
        when(accountDao.getAccountById(91L)).thenReturn(account);
        when(paramService.getParamByAccountIdAndType(91L,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), 0L))
                .thenReturn("001500100816801");
        when(paramService.getParamByAccountIdAndType(91L,
                PaymentChannel.PESA_LINK.findProperty(PaymentPropertyKeys.BANK_CODE), 0L))
                .thenReturn("0075");
    }

    private IPNCallbackDTO callback(String status, String accountDst, String tillNumber) {
        IPNCallbackDTO base = buildCallbackDTO();
        return new IPNCallbackDTO(base.sender(), base.recipient(), base.bankSrc(), base.bankDst(),
                base.accountSrc(), accountDst, base.rrn(), base.amount(), base.paymentReason(), status,
                base.phoneSrc(), base.phoneDst(), base.date(), base.transactionType(), base.originalAmount(),
                base.billReference(), tillNumber);
    }

    private String ipnSignature(IPNCallbackDTO callback) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        byte[] hmac = org.pms.silverocean.common.PMSUtils.signDataUsingHmacSha1(
                IPN_PASSWORD.getBytes(StandardCharsets.UTF_8),
                mapper.writeValueAsString(callback).getBytes(StandardCharsets.UTF_8));
        return hex(hmac);
    }

    private PesalinkValidatePaymentRequestDTO signedValidationRequest(String requestId, String billRef,
                                                                       BigDecimal amount) throws Exception {
        LinkedHashMap<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("requestId", requestId);
        unsigned.put("billRef", billRef);
        unsigned.put("amount", amount);
        byte[] hmac = org.pms.silverocean.common.PMSUtils.signDataUsingHmacSha1(
                IPN_PASSWORD.getBytes(StandardCharsets.UTF_8),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(unsigned)
                        .getBytes(StandardCharsets.UTF_8));
        return new PesalinkValidatePaymentRequestDTO(requestId, hex(hmac), billRef, amount);
    }

    private String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }

}
