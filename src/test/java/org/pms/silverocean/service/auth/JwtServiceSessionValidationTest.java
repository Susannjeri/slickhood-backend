package org.pms.silverocean.service.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.security.KeyDao;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceSessionValidationTest {
    @Mock RoleService roles;
    @Mock ConfigService config;
    @Mock KeyDao keys;
    @Mock UserDao users;

    @Test
    void sessionValidationUsesAuthoritativeTokenInsteadOfCachedUserEntity() {
        JwtService service = new JwtService(roles, config, keys, users);
        when(users.findCurrentSessionToken("owner@example.com")).thenReturn(Optional.of("current-session"));

        assertTrue(service.isCurrentSession("owner@example.com", "current-session"));
        assertFalse(service.isCurrentSession("owner@example.com", "replaced-session"));

        verify(users, never()).findByEmail("owner@example.com");
    }

    @Test
    void blankSessionClaimIsRejectedWithoutARepositoryLookup() {
        JwtService service = new JwtService(roles, config, keys, users);

        assertFalse(service.isCurrentSession("owner@example.com", " "));

        verify(users, never()).findCurrentSessionToken("owner@example.com");
    }
}
