package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.affiliate.AffiliateService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.totp.TotpService;
import org.pms.silverocean.service.auth.totp.TotpServiceFactory;
import org.pms.silverocean.service.auth.totp.impl.OtpType;
import org.pms.silverocean.service.geolocation.GeoLocationService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifiedSessionServiceTest {
    @Mock UserDao users;
    @Mock PasswordEncoder passwords;
    @Mock GeoLocationService locations;
    @Mock RoleService roles;
    @Mock GoogleAuthService google;
    @Mock I18NService i18n;
    @Mock LoginAttemptService attempts;
    @Mock JwtService jwt;
    @Mock TotpServiceFactory totpFactory;
    @Mock TotpService emailOtp;
    @Mock AffiliateService affiliates;
    UserAuthenticationService service;

    @BeforeEach
    void setUp() {
        when(totpFactory.getService(OtpType.EMAIL)).thenReturn(Optional.of(emailOtp));
        service = new UserAuthenticationService(users, passwords, locations, roles, google, i18n,
                attempts, jwt, totpFactory, affiliates);
    }

    @Test
    void verifiedUserReceivesJwtAndPersistedRefreshSession() {
        Users user = Users.builder().email("verified@example.com").build();
        user.setActive(true);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwt.generateJWT(user.getEmail())).thenReturn("access-jwt");

        var session = service.createSessionForVerifiedUser(user.getEmail());

        assertEquals("access-jwt", session.jwt());
        assertNotNull(session.refreshToken());
        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(users).save(saved.capture());
        assertNotNull(saved.getValue().getRefreshToken());
        assertNotEquals(session.refreshToken(), saved.getValue().getRefreshToken());
        verify(attempts).loginSuccess(user.getEmail());
    }
}
