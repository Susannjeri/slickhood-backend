package org.pms.silverocean.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.notification.sms.SMSService;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.platforms.mpesa.MPesaResultCodes;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MPesaPaymentResponseDTO;
import org.pms.silverocean.service.payment.platforms.mpesa.wrappers.MpesaCallbackDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CallBackControllerTest {
    @Mock private PaymentPlatformFactory paymentPlatformFactory;
    @Mock private PaymentPlatform paymentPlatform;
    @Mock private SMSService smsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CallBackController controller = new CallBackController(paymentPlatformFactory, smsService);
        ReflectionTestUtils.setField(controller, "mpesaCallbackToken", "test-callback-token");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void bankRouteNormalizesAndReconcilesUsingMpesaReceipt() throws Exception {
        when(paymentPlatformFactory.getPlatform(PaymentChannel.MPESA_BANK)).thenReturn(paymentPlatform);
        when(paymentPlatform.handleCallBack(any())).thenReturn(new MPesaPaymentResponseDTO(MPesaResultCodes.VALID));

        mockMvc.perform(post("/callback/mpesa/bank/confirmation")
                        .param("token", "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mpesaReference":"UCMEWA74WV",
                                  "bankReference":"BANK-12345",
                                  "invoiceReference":"INV-300",
                                  "amount":300.00,
                                  "transactionTime":"20260826205000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value("0"));

        ArgumentCaptor<MpesaCallbackDTO> callback = ArgumentCaptor.forClass(MpesaCallbackDTO.class);
        verify(paymentPlatform).handleCallBack(callback.capture());
        assertEquals("UCMEWA74WV", callback.getValue().mpesaPaymentDTO().transId());
        assertEquals("BANK-12345", callback.getValue().mpesaPaymentDTO().thirdPartyTransId());
        assertEquals("INV-300", callback.getValue().mpesaPaymentDTO().billRefNumber());
    }

    @Test
    void bankRouteRejectsWrongCallbackToken() throws Exception {
        mockMvc.perform(post("/callback/mpesa/bank/confirmation")
                        .param("token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mpesaReference":"UCMEWA74WV",
                                  "bankReference":"BANK-12345",
                                  "invoiceReference":"INV-300",
                                  "amount":300.00,
                                  "transactionTime":"20260826205000"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(paymentPlatformFactory, never()).getPlatform(any());
    }
}
