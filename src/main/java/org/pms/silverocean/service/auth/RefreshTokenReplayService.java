package org.pms.silverocean.service.auth;

import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.security.KeyDao;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * Reproduces one refresh-token replacement without persisting a recoverable
 * bearer credential. Domain-separated HMAC keeps the result unpredictable to
 * anyone who can read the database but does not hold the application key.
 */
@Service
public class RefreshTokenReplayService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] TOKEN_DOMAIN = "slickhood-refresh-replacement-token:v1"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] REQUEST_ID_DOMAIN = "slickhood-refresh-replacement-request:v1"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] LEGACY_UPGRADE_CONTEXT = "slickhood-legacy-refresh-upgrade:v1"
            .getBytes(StandardCharsets.UTF_8);
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final KeyDao keyDao;

    public RefreshTokenReplayService(KeyDao keyDao) {
        this.keyDao = keyDao;
    }

    public Replacement issue(String consumedRefreshToken, String requestId) {
        return derive(keyDao.getActiveSecretKey(), consumedRefreshToken, requestId);
    }

    /**
     * A key rotation or restart may occur during the short replay window. Try
     * the active key first, then retained historical keys, and return only the
     * candidate whose hash is still the authoritative current session token.
     */
    public Optional<Replacement> recover(String consumedRefreshToken, String requestId,
                                         String currentRefreshTokenHash,
                                         String currentRequestIdHash) {
        Replacement activeCandidate = derive(keyDao.getActiveSecretKey(), consumedRefreshToken, requestId);
        if (matches(activeCandidate, currentRefreshTokenHash, currentRequestIdHash)) {
            return Optional.of(activeCandidate);
        }
        return keyDao.getOldKeys().stream()
                .map(key -> derive(key, consumedRefreshToken, requestId))
                .filter(candidate -> matches(candidate, currentRefreshTokenHash, currentRequestIdHash))
                .findFirst();
    }

    /**
     * Recognizes the deterministic transition id used by the web tier when an
     * older API issued a token without a companion id. This also repairs the
     * narrow mixed-version case where an old API rotates a token but cannot
     * clear companion-id columns written earlier by a new API instance.
     */
    public boolean isLegacyTransitionRequestId(String refreshToken, String requestId) {
        if (refreshToken == null || requestId == null) return false;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(refreshToken.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = URL_ENCODER.encodeToString(mac.doFinal(LEGACY_UPGRADE_CONTEXT));
            return constantTimeEquals(expected, requestId);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not verify legacy refresh transition", exception);
        }
    }

    private Replacement derive(byte[] key, String consumedRefreshToken, String requestId) {
        return new Replacement(
                derive(key, TOKEN_DOMAIN, consumedRefreshToken, requestId),
                derive(key, REQUEST_ID_DOMAIN, consumedRefreshToken, requestId));
    }

    private String derive(byte[] key, byte[] domain, String consumedRefreshToken, String requestId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            mac.update(domain);
            mac.update((byte) 0);
            mac.update(consumedRefreshToken.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return URL_ENCODER.encodeToString(mac.doFinal(requestId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not derive refresh-token replacement", exception);
        }
    }

    private boolean matches(Replacement candidate, String currentRefreshTokenHash,
                            String currentRequestIdHash) {
        return constantTimeEquals(PMSUtils.hashToken(candidate.refreshToken()), currentRefreshTokenHash)
                && constantTimeEquals(PMSUtils.hashToken(candidate.requestId()), currentRequestIdHash);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    public record Replacement(String refreshToken, String requestId) {
    }
}
