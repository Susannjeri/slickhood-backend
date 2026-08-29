package org.pms.silverocean.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.payment.PaymentService;
import org.pms.silverocean.service.payment.PaymentReceiptService;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.wrappers.PaymentResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerIT {

    private static final String INVOICE_REF = "INV-TEST-001";
    private static final long ACCOUNT_ID = 1L;
    private static final String CUSTOM_PHONE_NUMBER = "+254799999999";

    @Mock private InvoiceService invoiceService;
    @Mock private PaymentService paymentService;
    @Mock private PaymentReceiptService paymentReceiptService;
    @Mock private I18NService i18NService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentController controller = new PaymentController(invoiceService, i18NService, paymentService, paymentReceiptService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(i18NService.getLocalizedMessage(ResponseCode.MPESA_PAYMENT_INITIALIZED))
                .thenReturn("Payment initialized");
    }

    @Test
    void initPayment_withoutPhoneNumber_passesNullForServiceResolution() throws Exception {
        when(invoiceService.initInvoicePayment(INVOICE_REF, PaymentChannel.MPESA, null, ACCOUNT_ID))
                .thenReturn(new PaymentResponse(true, ResponseCode.MPESA_PAYMENT_INITIALIZED));

        mockMvc.perform(get("/payment/init")
                        .param("invoiceRef", INVOICE_REF)
                        .param("paymentChannel", PaymentChannel.MPESA.name())
                        .param("accountId", String.valueOf(ACCOUNT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(invoiceService).initInvoicePayment(INVOICE_REF, PaymentChannel.MPESA, null, ACCOUNT_ID);
    }

    @Test
    void initPayment_withPhoneNumber_passesCustomMsisdn() throws Exception {
        when(invoiceService.initInvoicePayment(INVOICE_REF, PaymentChannel.MPESA, CUSTOM_PHONE_NUMBER, ACCOUNT_ID))
                .thenReturn(new PaymentResponse(true, ResponseCode.MPESA_PAYMENT_INITIALIZED));

        mockMvc.perform(get("/payment/init")
                        .param("invoiceRef", INVOICE_REF)
                        .param("paymentChannel", PaymentChannel.MPESA.name())
                        .param("phoneNumber", CUSTOM_PHONE_NUMBER)
                        .param("accountId", String.valueOf(ACCOUNT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(invoiceService).initInvoicePayment(
                INVOICE_REF, PaymentChannel.MPESA, CUSTOM_PHONE_NUMBER, ACCOUNT_ID);
    }
}
