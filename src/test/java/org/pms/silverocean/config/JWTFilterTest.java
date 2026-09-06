package org.pms.silverocean.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JWTFilterTest {
    @Mock private JwtService jwtService;
    @Mock private I18NService i18nService;
    @Mock private FilterChain chain;
    @Mock private Jws<Claims> parsedToken;

    private JWTFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JWTFilter(jwtService, i18nService);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOnlyTheAccessTokenBoundToTheCurrentSession() throws Exception {
        Claims claims = claims("current-session", List.of(role("Landlord", "create_property")));
        stubValidatedToken(claims, true);
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Landlord");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("owner@example.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").contains("ROLE_LANDLORD", "create_property");
    }

    @Test
    void rejectsAnAccessTokenAfterAnotherSessionReplacesIt() throws Exception {
        Claims claims = claims("old-session", List.of(role("Landlord", "create_property")));
        stubValidatedToken(claims, false);
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Landlord");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void multiRoleTokenRequiresAnExplicitActiveRole() throws Exception {
        when(i18nService.getLocalizedMessage(anyString())).thenReturn("Invalid token");
        stubValidatedToken(claims("current-session", List.of(
                role("Landlord", "view_property"),
                role("EstateManager", "manage_estate")
        )), true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid token");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void selectedRoleDoesNotInheritPermissionsFromOtherAssignedRoles() throws Exception {
        stubValidatedToken(claims("current-session", List.of(
                role("Landlord", "view_property"),
                role("EstateManager", "manage_estate")
        )), true);
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Landlord");

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE)).isEqualTo("Landlord");
        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_LANDLORD", "view_property")
                .doesNotContain("manage_estate");
        verify(chain).doFilter(request, response);
    }

    @Test
    void requestedRoleMustBeAssignedToTheToken() throws Exception {
        when(i18nService.getLocalizedMessage(anyString())).thenReturn("Invalid token");
        stubValidatedToken(claims("current-session", List.of(role("Landlord", "view_property"))), true);
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Superadmin");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void singleRoleTokenRemainsBackwardCompatibleWithoutAHeader() throws Exception {
        stubValidatedToken(claims("current-session", List.of(role("Tenant", "view_active_lease"))), true);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE)).isEqualTo("Tenant");
        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_TENANT", "view_active_lease");
        verify(chain).doFilter(request, response);
    }

    private void stubValidatedToken(Claims claims, boolean currentSession) {
        when(jwtService.validateToken("access-token")).thenReturn(parsedToken);
        when(parsedToken.getBody()).thenReturn(claims);
        when(jwtService.isCurrentSession("owner@example.com", claims.get(JwtService.SESSION_ID, String.class)))
                .thenReturn(currentSession);
    }

    private Claims claims(String sessionId, List<Map<String, Object>> roles) {
        Claims claims = Jwts.claims();
        claims.setSubject("owner@example.com");
        claims.put(JwtService.SESSION_ID, sessionId);
        claims.put(JwtService.ROLES, roles);
        return claims;
    }

    private Map<String, Object> role(String title, String... permissions) {
        return Map.of("title", title, "permissions", List.of(permissions));
    }
}
