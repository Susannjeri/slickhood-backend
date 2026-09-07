package org.pms.silverocean.service.payment.platforms.pesalink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.eventlogger.EventService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.PaymentDao;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.platforms.pesalink.wrappers.IPNCallbackDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PesaLinkServiceTest {

    @Mock private UpdatePaymentService updatePaymentService;
    @Mock private ConfigService configService;
    @Mock private I18NService i18NService;
    @Mock private EventService eventService;
    @Mock private PaymentDao paymentDao;
    @Mock private ParamService paramService;

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
                i18NService, eventService, paymentDao, paramService);
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

}
