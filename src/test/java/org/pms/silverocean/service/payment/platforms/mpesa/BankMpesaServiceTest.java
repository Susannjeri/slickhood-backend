package org.pms.silverocean.service.payment.platforms.mpesa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.param.ParamService;
import org.pms.silverocean.service.payment.UpdatePaymentService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentPropertyKeys;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class BankMpesaServiceTest {
    @Mock private UpdatePaymentService updatePaymentService;
    @Mock private ConfigService configService;
    @Mock private ParamService paramService;
    @Mock private I18NService i18NService;
    @Mock private MPesaService directMpesaService;

    @Test
    void returnsInvoiceBoundBankPaybillInstructionsWithoutClaimingPaymentSuccess() {
        BankMpesaService service = new BankMpesaService(updatePaymentService, configService,
                paramService, i18NService, directMpesaService);
        PMSInvoice invoice = new PMSInvoice();
        invoice.setRef("INV-ABC");
        invoice.setPropertyId(22L);
        invoice.setPendingAmount(6501);

        when(paramService.getParamByAccountIdAndType(8L,
                PaymentChannel.MPESA_BANK.findProperty(PaymentPropertyKeys.BANK_PAYBILL), 22L))
                .thenReturn("222111");
        when(paramService.getParamByAccountIdAndType(8L,
                PaymentChannel.MPESA_BANK.findProperty(PaymentPropertyKeys.BANK_ACCOUNT), 22L))
                .thenReturn("12345");
        when(i18NService.getLocalizedMessage(ResponseCode.MPESA_BANK_PAYMENT_INSTRUCTIONS))
                .thenReturn("Paybill %s account %s#%s");

        PaymentResponse response = service.processPayment(invoice, null, 8L);

        assertTrue(response.success());
        assertEquals(ResponseCode.MPESA_BANK_PAYMENT_INSTRUCTIONS, response.responseCode());
        assertEquals("Paybill 222111 account 12345#INV-ABC", response.body());
        assertFalse(invoice.isTransactionInProgress());
        verify(updatePaymentService, times(2)).updateInvoice(invoice);
    }
}
