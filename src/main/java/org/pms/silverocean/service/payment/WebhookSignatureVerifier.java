package org.pms.silverocean.service.payment;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

public final class WebhookSignatureVerifier {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String HMAC_SHA_512 = "HmacSHA512";

    private WebhookSignatureVerifier() {
    }

    public static boolean isFlutterwaveSignatureValid(String rawBody, String signature,
                                                       String legacySignature, String secretHash) {
        if (StringUtils.isAnyBlank(rawBody, secretHash)) {
            return false;
        }

        if (StringUtils.isNotBlank(signature)) {
            try {
                Mac mac = Mac.getInstance(HMAC_SHA_256);
                mac.init(new SecretKeySpec(secretHash.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
                String expected = Base64.getEncoder().encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
                return constantTimeEquals(expected, signature);
            } catch (GeneralSecurityException ignored) {
                return false;
            }
        }

        // Flutterwave v3 installations can still send the configured secret in verif-hash.
        return StringUtils.isNotBlank(legacySignature) && constantTimeEquals(secretHash, legacySignature);
    }

    public static boolean isSharedTokenValid(String suppliedToken, String expectedToken) {
        return StringUtils.isNoneBlank(suppliedToken, expectedToken)
                && constantTimeEquals(expectedToken, suppliedToken);
    }

    public static boolean isPaystackSignatureValid(String rawBody, String signature, String secretKey) {
        if (StringUtils.isAnyBlank(rawBody, signature, secretKey)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_512);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_512));
            StringBuilder expected = new StringBuilder();
            for (byte value : mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8))) {
                expected.append(String.format("%02x", value));
            }
            return constantTimeEquals(expected.toString(), signature.toLowerCase(java.util.Locale.ROOT));
        } catch (GeneralSecurityException ignored) {
            return false;
        }
    }

    public static boolean isMetaSignatureValid(String rawBody, String signature, String appSecret) {
        if (StringUtils.isAnyBlank(rawBody, signature, appSecret) || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            StringBuilder expected = new StringBuilder("sha256=");
            for (byte value : mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8))) {
                expected.append(String.format("%02x", value));
            }
            return constantTimeEquals(expected.toString(), signature.toLowerCase(java.util.Locale.ROOT));
        } catch (GeneralSecurityException ignored) {
            return false;
        }
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
