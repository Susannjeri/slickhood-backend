package org.pms.silverocean.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enforces customer KYC at the API boundary. Pending customers retain a valid
 * session solely to finish verification; hiding frontend routes is not relied
 * upon as a security control.
 */
@Component
@RequiredArgsConstructor
public class AccountActivationFilter extends OncePerRequestFilter {
    private static final Set<PMSRole> INTERNAL_ROLES = Set.of(
            PMSRole.SUPER_ADMIN, PMSRole.FINANCE, PMSRole.INSURANCE_ADVISER,
            PMSRole.INSURANCE_MANAGER, PMSRole.GUARD, PMSRole.PROPERTY_MANAGER);
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/auth/", "/otp/", "/kyc/", "/role/", "/helpdesk/",
            "/user/details", "/user/verify/contact", "/user/update/contact",
            "/actuator/health", "/error", "/deployed-hash", "/invite/validate",
            "/sp/directory/", "/soko/catalog/", "/affiliate/public/");

    private final UserDao userDao;
    private final I18NService i18NService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()) || allowed(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        var user = userDao.getUserObject();
        PMSRole activeRole = userDao.getActiveRole();
        if (user != null && (AccountStatus.ACTIVE.name().equals(user.getAccountStatus())
                || INTERNAL_ROLES.contains(activeRole))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        String description = i18NService.getLocalizedMessage(ResponseCode.KYC_ACCOUNT_RESTRICTED);
        if (description == null || description.isBlank()) {
            description = "Complete identity verification before using this feature.";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (user != null) data.put("accountStatus", user.getAccountStatus());
        data.put("destination", "/kyc");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", ResponseCode.KYC_ACCOUNT_RESTRICTED.getCode());
        body.put("description", description);
        body.put("data", data);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean allowed(String path) {
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
