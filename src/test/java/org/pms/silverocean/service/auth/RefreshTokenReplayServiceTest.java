package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.service.security.KeyDao;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenReplayServiceTest {
    @Mock KeyDao keyDao;

    @Test
    void sameConsumedPairDerivesTheSameDistinctReplacementSecrets() {
        when(keyDao.getActiveSecretKey()).thenReturn(key("active-backend-key-material"));
        RefreshTokenReplayService service = new RefreshTokenReplayService(keyDao);

        var first = service.issue("consumed-refresh-token",
                "d8886f66-9a57-44af-9cb8-3a3b12929dd5");
        var second = service.issue("consumed-refresh-token",
                "d8886f66-9a57-44af-9cb8-3a3b12929dd5");

        assertEquals(first, second);
        assertNotEquals(first.refreshToken(), first.requestId());
        assertEquals(43, first.refreshToken().length());
        assertEquals(43, first.requestId().length());
    }

    @Test
    void recoveryCanUseTheIssuingKeyAfterBackendKeyRotation() {
        byte[] issuingKey = key("issuing-backend-key-material");
        byte[] rotatedKey = key("rotated-backend-key-material");
        when(keyDao.getActiveSecretKey()).thenReturn(issuingKey);
        RefreshTokenReplayService service = new RefreshTokenReplayService(keyDao);
        var issued = service.issue("consumed-refresh-token",
                "a046729b-78db-45b1-97a2-fcbff854b4f5");
        when(keyDao.getActiveSecretKey()).thenReturn(rotatedKey);
        when(keyDao.getOldKeys()).thenReturn(Set.of(issuingKey));

        var recovered = service.recover("consumed-refresh-token",
                "a046729b-78db-45b1-97a2-fcbff854b4f5",
                PMSUtils.hashToken(issued.refreshToken()), PMSUtils.hashToken(issued.requestId()));

        assertEquals(issued, recovered.orElseThrow());
    }

    @Test
    void recoveryFailsWhenEitherCurrentHashDoesNotMatch() {
        when(keyDao.getActiveSecretKey()).thenReturn(key("active-backend-key-material"));
        when(keyDao.getOldKeys()).thenReturn(Set.of());
        RefreshTokenReplayService service = new RefreshTokenReplayService(keyDao);
        var issued = service.issue("consumed-refresh-token",
                "300e4cb2-129d-4bc0-b0f8-a8a6c3b849bc");

        assertFalse(service.recover("consumed-refresh-token",
                "300e4cb2-129d-4bc0-b0f8-a8a6c3b849bc",
                PMSUtils.hashToken(issued.refreshToken()), PMSUtils.hashToken("wrong-request-id")).isPresent());
    }

    @Test
    void legacyTransitionIdMatchesTheWebCryptoContract() {
        RefreshTokenReplayService service = new RefreshTokenReplayService(keyDao);

        assertTrue(service.isLegacyTransitionRequestId(
                "legacy-refresh-token",
                "JDjwzzlg0ufgb2vPi_O-3wzQLjW3ExlcYnUGYqw4sFU"));
        assertFalse(service.isLegacyTransitionRequestId(
                "legacy-refresh-token",
                "KDjwzzlg0ufgb2vPi_O-3wzQLjW3ExlcYnUGYqw4sFU"));
    }

    private byte[] key(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
