package org.pms.silverocean.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.notification.sms.SMSService;
import org.pms.silverocean.service.payment.PaymentCallBackResponse;
import org.pms.silverocean.service.payment.PaymentPlatform;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallBackControllerWebhookTest {
    @Mock PaymentPlatformFactory platforms;
    @Mock PaymentPlatform platform;
    @Mock PaymentCallBackResponse response;
    @Mock SMSService sms;
    @Mock HttpServletRequest request;
    CallBackController controller;

    @BeforeEach void setup() {
        controller = new CallBackController(platforms, sms);
        ReflectionTestUtils.setField(controller, "paystackSecretKey", "test-secret");
        ReflectionTestUtils.setField(controller, "flutterwaveWebhookSecret", "fw-secret");
        ReflectionTestUtils.setField(controller, "mpesaCallbackToken", "mpesa-token");
        ReflectionTestUtils.setField(controller, "whatsAppAppSecret", "meta-secret");
        org.mockito.Mockito.lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test void rejectsInvalidPaystackSignatureBeforeDispatch() {
        var result = controller.receivePaystackCallback(request, "bad-signature", "{}");
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(platforms, never()).getPlatform(any());
    }

    @Test void validPaystackSignatureDispatchesExactlyOnce() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"31\"}}";
        when(platforms.getPlatform(PaymentChannel.PAYSTACK)).thenReturn(platform);
        when(platform.handleCallBack(any())).thenReturn(response);
        var result = controller.receivePaystackCallback(request, hmac(body, "test-secret"), body);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(platform).handleCallBack(any());
    }

    @Test void mpesaSharedTokenIsMandatory() {
        var result = controller.receiveMPesaConfirmationReq(request, "wrong", null, null);
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(platforms, never()).getPlatform(any());
    }

    @Test void rejectsInvalidWhatsAppSignatureBeforeProcessing() {
        var result = controller.handleIncomingEvents(request, "sha256=bad", "{\"object\":\"whatsapp_business_account\"}");
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(sms, never()).receiveWhatsAppCallback(any(), any());
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder signature = new StringBuilder();
        for (byte value : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) signature.append(String.format("%02x", value));
        return signature.toString();
    }

    @Test void verifiesOnlySubscribeWithANonEmptyMatchingToken() {
        ReflectionTestUtils.setField(controller, "whatsAppVerifyToken", "verify-test");
        assertEquals("challenge", controller.verifyWebhook("subscribe", "verify-test", "challenge").getBody());
        assertEquals(HttpStatus.FORBIDDEN, controller.verifyWebhook("unsubscribe", "verify-test", "challenge").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.verifyWebhook("subscribe", "wrong", "challenge").getStatusCode());
        ReflectionTestUtils.setField(controller, "whatsAppVerifyToken", "");
        assertEquals(HttpStatus.FORBIDDEN, controller.verifyWebhook("subscribe", "", "challenge").getStatusCode());
    }

    @Test void authenticatedMalformedJsonReturnsBadRequest() throws Exception {
        String body = "{invalid";
        assertEquals(HttpStatus.BAD_REQUEST, controller.handleIncomingEvents(request, metaSignature(body), body).getStatusCode());
        verify(sms, never()).receiveWhatsAppCallback(any(), any());
    }

    @Test void authenticatedCallbackParsesSnakeCaseFieldsAndIgnoresUnrelatedFields() throws Exception {
        String body = """
          {"object":"whatsapp_business_account","entry":[{"id":"a","changes":[{"field":"messages","value":{
          "messaging_product":"whatsapp","metadata":{"phone_number_id":"123"},
          "statuses":[{"id":"b","status":"read","recipient_id":"254700000000"}],"messages":[]}}]}]}
          """;
        assertEquals(HttpStatus.OK, controller.handleIncomingEvents(request, metaSignature(body), body).getStatusCode());
        var captured = org.mockito.ArgumentCaptor.forClass(org.pms.silverocean.service.notification.sms.whatsapp.wrappers.callback.WAWebHook.class);
        verify(sms).receiveWhatsAppCallback(captured.capture(), any());
        var value = captured.getValue().entry().getFirst().changes().getFirst().value();
        assertEquals("123", value.metadata().phoneNumberId());
        assertEquals("254700000000", value.statuses().getFirst().recipientId());
    }
    private String metaSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("meta-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
