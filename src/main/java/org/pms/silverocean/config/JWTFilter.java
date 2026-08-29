package org.pms.silverocean.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.common.StaticStrings;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JWTFilter extends GenericFilter {
    public static final String ACTIVE_ROLE_HEADER = "X-Slickhood-Role";
    public static final String ACTIVE_ROLE_ATTRIBUTE = "slickhood.activeRole";
    private final JwtService jwtService;
    private final I18NService i18nService;

    public JWTFilter(JwtService jwtService, I18NService i18nService) {
        this.jwtService = jwtService;
        this.i18nService = i18nService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String header = httpRequest.getHeader("Authorization");

        if (StringUtils.isNotBlank(header) && header.startsWith(StaticStrings.BEARER_PREFIX)) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validateToken(token).getBody();
                String user = claims.getSubject();
                boolean refreshTokenPresent = jwtService.checkIfRefreshTokenIsPresent(user);
                if (!refreshTokenPresent) {
                    throw new RuntimeException("User is not logged In");
                }

                List<Map<String, Object>> roles = claims.get(JwtService.ROLES, List.class);
                Set<GrantedAuthority> authorities = new HashSet<>();
                if (!CollectionUtils.isEmpty(roles)) {
                    String requestedRole = httpRequest.getHeader(ACTIVE_ROLE_HEADER);
                    Map<String, Object> selectedRole = selectActiveRole(roles, requestedRole);
                    if (selectedRole == null) {
                        throw new IllegalArgumentException("A valid active role is required");
                    }
                    for (Map<String, Object> role : List.of(selectedRole)) {
                        Object title = role.get("title");
                        if (title instanceof String roleName) {
                            authorities.add(
                                    new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
                            );
                            httpRequest.setAttribute(ACTIVE_ROLE_ATTRIBUTE, roleName);
                        }

                        Object perms = role.get("permissions");
                        if (perms instanceof List<?> permissions) {
                            permissions.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(SimpleGrantedAuthority::new)
                                    .forEach(authorities::add);
                        }
                    }
                }

                var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json;charset=utf-8");
                httpResponse.getWriter().write("{\"success\": false, \"code\":\"" + ResponseCode.INVALID_OR_EXPIRED_TOKEN.getCode() + "\", \"description\":\"" + i18nService.getLocalizedMessage(ResponseCode.INVALID_OR_EXPIRED_TOKEN.getDescription()) + "\", \"data\":[]}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Map<String, Object> selectActiveRole(List<Map<String, Object>> roles, String requestedRole) {
        if (roles.size() == 1 && StringUtils.isBlank(requestedRole)) {
            return roles.getFirst();
        }
        if (StringUtils.isBlank(requestedRole)) {
            return null;
        }
        String normalizedRequested = normalizeRole(requestedRole);
        return roles.stream()
                .filter(role -> role.get("title") instanceof String)
                .filter(role -> normalizeRole(role.get("title").toString()).equals(normalizedRequested))
                .findFirst()
                .orElse(null);
    }

    private String normalizeRole(String role) {
        return role.replace("_", "").replace(" ", "").toUpperCase();
    }

}
