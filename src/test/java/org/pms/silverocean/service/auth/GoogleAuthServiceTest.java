package org.pms.silverocean.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleAuthServiceTest {

    @Test
    void malformedGoogleTokenIsRejectedWithoutEscapingAsServerError() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify("malformed-token")).thenThrow(new IllegalArgumentException("bad token"));
        GoogleAuthService service = new GoogleAuthService();
        ReflectionTestUtils.setField(service, "verifier", verifier);

        assertThat(service.verifyIdToken("malformed-token")).isEmpty();
    }
}
