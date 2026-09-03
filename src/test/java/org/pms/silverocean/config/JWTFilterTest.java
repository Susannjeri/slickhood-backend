package org.pms.silverocean.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JWTFilterTest {
    @Mock JwtService jwtService;
    @Mock I18NService i18nService;
    @Mock Jws<Claims> parsedToken;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOnlyTheAccessTokenBoundToTheCurrentSession() throws Exception {
        Claims claims = claims("current-session");
        when(jwtService.validateToken("access-token")).thenReturn(parsedToken);
        when(parsedToken.getBody()).thenReturn(claims);
        when(jwtService.isCurrentSession("owner@example.com", "current-session")).thenReturn(true);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        new JWTFilter(jwtService, i18nService).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("owner@example.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").contains("ROLE_LANDLORD", "create_property");
    }

    @Test
    void rejectsAnAccessTokenAfterAnotherSessionReplacesIt() throws Exception {
        Claims claims = claims("old-session");
        when(jwtService.validateToken("access-token")).thenReturn(parsedToken);
        when(parsedToken.getBody()).thenReturn(claims);
        when(jwtService.isCurrentSession("owner@example.com", "old-session")).thenReturn(false);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        new JWTFilter(jwtService, i18nService).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    private Claims claims(String sessionId) {
        Claims claims = Jwts.claims();
        claims.setSubject("owner@example.com");
        claims.put(JwtService.SESSION_ID, sessionId);
        claims.put(JwtService.ROLES, List.of(Map.of(
                "title", "Landlord",
                "permissions", List.of("create_property"))));
        return claims;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Landlord");
        return request;
    }
}
