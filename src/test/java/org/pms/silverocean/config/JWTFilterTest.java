package org.pms.silverocean.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JWTFilterTest {
    @Mock private JwtService jwtService;
    @Mock private I18NService i18NService;
    @Mock private FilterChain chain;

    private JWTFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JWTFilter(jwtService, i18NService);
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void multiRoleTokenRequiresAnExplicitActiveRole() throws Exception {
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Invalid token");
        stubToken(List.of(
                role("Landlord", "view_property"),
                role("EstateManager", "manage_estate")
        ));

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void selectedRoleDoesNotInheritPermissionsFromOtherAssignedRoles() throws Exception {
        stubToken(List.of(
                role("Landlord", "view_property"),
                role("EstateManager", "manage_estate")
        ));
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Landlord");

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(200, response.getStatus());
        assertEquals("Landlord", request.getAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE));
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_LANDLORD")));
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("view_property")));
        assertFalse(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("manage_estate")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void requestedRoleMustBeAssignedToTheToken() throws Exception {
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Invalid token");
        stubToken(List.of(role("Landlord", "view_property")));
        request.addHeader(JWTFilter.ACTIVE_ROLE_HEADER, "Superadmin");

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void singleRoleTokenRemainsBackwardCompatibleWithoutAHeader() throws Exception {
        stubToken(List.of(role("Tenant", "view_active_lease")));

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(200, response.getStatus());
        assertEquals("Tenant", request.getAttribute(JWTFilter.ACTIVE_ROLE_ATTRIBUTE));
        assertTrue(authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("view_active_lease")));
        verify(chain).doFilter(request, response);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubToken(List<Map<String, Object>> roles) {
        Jws<Claims> parsedToken = mock(Jws.class);
        Claims claims = mock(Claims.class);
        when(jwtService.validateToken("test-token")).thenReturn(parsedToken);
        when(parsedToken.getBody()).thenReturn(claims);
        when(claims.getSubject()).thenReturn("multi-role@example.com");
        when(claims.get(JwtService.ROLES, List.class)).thenReturn((List) roles);
        when(jwtService.checkIfRefreshTokenIsPresent("multi-role@example.com")).thenReturn(true);
    }

    private Map<String, Object> role(String title, String... permissions) {
        return Map.of("title", title, "permissions", List.of(permissions));
    }
}
