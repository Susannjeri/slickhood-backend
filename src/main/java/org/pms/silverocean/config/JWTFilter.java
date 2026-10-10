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
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
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
        // Rider assignment links carry their own narrow, assignment-scoped
        // bearer. Ignore a stale browser login on this public endpoint so an
        // unrelated expired session cannot prevent the rider from responding.
        if (isPublicRiderAssignment(httpRequest)) {
            httpResponse.setHeader("Cache-Control", "no-store, max-age=0");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setHeader("Referrer-Policy", "no-referrer");
            chain.doFilter(request, response);
            return;
        }
        String header = httpRequest.getHeader("Authorization");

        if (StringUtils.isNotBlank(header) && header.startsWith(StaticStrings.BEARER_PREFIX)) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validateToken(token).getBody();
                String user = claims.getSubject();
                String sessionId = claims.get(JwtService.SESSION_ID, String.class);
                if (!jwtService.isCurrentSession(user, sessionId)) {
                    throw new RuntimeException("Session was replaced or ended");
                }

                List<Map<String, Object>> roles = claims.get(JwtService.ROLES, List.class);
                Set<GrantedAuthority> authorities = new HashSet<>();
                if (!CollectionUtils.isEmpty(roles)) {
                    String requestedRole = httpRequest.getHeader(ACTIVE_ROLE_HEADER);
                    Map<String, Object> selectedRole = selectActiveRole(roles, requestedRole);
                    // This read-only account-state check is role agnostic. A
                    // refreshed token may no longer contain the role persisted
                    // by an older browser tab; authenticate it with an assigned
                    // role so the client can reconcile instead of misreporting
                    // the valid session as an expired token. All operational
                    // endpoints continue to require an explicitly assigned role.
                    if (selectedRole == null && isRoleAgnosticRecoveryRead(httpRequest)) {
                        selectedRole = roles.getFirst();
                    }
                    if (selectedRole == null) {
                        throw new IllegalArgumentException("A valid active role is required");
                    }
                    for (Map<String, Object> role : List.of(selectedRole)) {
                        Object title = role.get("title");
                        if (title instanceof String roleName) {
                            authorities.add(
                                    new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
                            );
                            Arrays.stream(PMSRole.values())
                                    .filter(knownRole -> normalizeRole(knownRole.getName()).equals(normalizeRole(roleName)))
                                    .findFirst()
                                    .map(PMSRole::name)
                                    .map(name -> new SimpleGrantedAuthority("ROLE_" + name))
                                    .ifPresent(authorities::add);
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
            } catch (DataAccessException e) {
                // A valid session must not be reported as an expired token just
                // because the authoritative session lookup temporarily failed.
                // 503 keeps the browser session intact and allows a bounded retry.
                httpResponse.setHeader("Retry-After", "3");
                writeFailure(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        ResponseCode.SOMETHING_WENT_WRONG);
                return;
            } catch (Exception e) {
                writeFailure(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        ResponseCode.INVALID_OR_EXPIRED_TOKEN);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void writeFailure(HttpServletResponse response, int status, ResponseCode code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"success\": false, \"code\":\"" + code.getCode()
                + "\", \"description\":\"" + i18nService.getLocalizedMessage(code.getDescription())
                + "\", \"data\":[]}");
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

    private boolean isRoleAgnosticRecoveryRead(HttpServletRequest request) {
        String path = request.getServletPath();
        if (StringUtils.isBlank(path)) path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return Set.of("/kyc/access-status", "/kyc/current").contains(path);
    }

    private boolean isPublicRiderAssignment(HttpServletRequest request) {
        String path = request.getServletPath();
        if (StringUtils.isBlank(path)) path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.equals("/soko/public/rider-assignment")
                || path.startsWith("/soko/public/rider-assignment/");
    }

    private String normalizeRole(String role) {
        return role.replace("_", "").replace(" ", "").toUpperCase();
    }

}
