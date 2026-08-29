package org.pms.silverocean.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.RegistrationChannel;
import org.pms.silverocean.database.pms.entities.Users;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Service
@Slf4j
public class GoogleAuthService {
    private GoogleIdTokenVerifier verifier;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @PostConstruct
    private void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .setIssuer("https://accounts.google.com")
                .build();
    }

    public Optional<Users> verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken verifiedToken = verifier.verify(idTokenString);
            if (verifiedToken != null) {
                GoogleIdToken.Payload payload = verifiedToken.getPayload();
                String email = payload.getEmail();
                String userId = payload.getSubject();
                String name = String.valueOf(payload.get("name"));
                return Optional.of(Users.builder().googleId(userId).email(email).fullName(name).source(RegistrationChannel.GOOGLE.name()).build());
            }
        } catch (GeneralSecurityException | IOException e) {
            log.error("Google ID Token verification failed: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
