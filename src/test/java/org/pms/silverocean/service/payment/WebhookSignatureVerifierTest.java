package org.pms.silverocean.service.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {
    @Test
    void validatesFlutterwaveHmacSignature() throws Exception {
        String body = "{\"event\":\"charge.completed\"}";
        String secret = "webhook-secret";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertTrue(WebhookSignatureVerifier.isFlutterwaveSignatureValid(body, signature, null, secret));
        assertFalse(WebhookSignatureVerifier.isFlutterwaveSignatureValid(body + " ", signature, null, secret));
    }

    @Test
    void validatesLegacyFlutterwaveHashAndMpesaToken() {
        assertTrue(WebhookSignatureVerifier.isFlutterwaveSignatureValid("{}", null, "secret", "secret"));
        assertTrue(WebhookSignatureVerifier.isSharedTokenValid("token", "token"));
        assertFalse(WebhookSignatureVerifier.isSharedTokenValid("wrong", "token"));
        assertFalse(WebhookSignatureVerifier.isSharedTokenValid("", ""));
    }

    @Test
    void validatesMetaSha256Signature() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";
        String secret = "meta-app-secret";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder signature = new StringBuilder("sha256=");
        for (byte value : mac.doFinal(body.getBytes(StandardCharsets.UTF_8))) {
            signature.append(String.format("%02x", value));
        }

        assertTrue(WebhookSignatureVerifier.isMetaSignatureValid(body, signature.toString(), secret));
        assertFalse(WebhookSignatureVerifier.isMetaSignatureValid(body + " ", signature.toString(), secret));
        assertFalse(WebhookSignatureVerifier.isMetaSignatureValid(body, null, secret));
    }
}
