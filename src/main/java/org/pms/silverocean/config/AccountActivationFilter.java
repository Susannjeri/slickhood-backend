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
import org.pms.silverocean.database.pms.WorkspaceMembershipRepo;
import org.pms.silverocean.service.teamaccess.TeamMembershipRole;
import org.pms.silverocean.service.teamaccess.TeamMembershipStatus;
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
            PMSRole.SUPER_ADMIN, PMSRole.SUPPORT, PMSRole.SALES_MARKETING, PMSRole.FINANCE, PMSRole.INSURANCE_ADVISER,
            PMSRole.INSURANCE_MANAGER);
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/auth/", "/otp/", "/kyc/", "/role/", "/helpdesk/",
            "/user/details", "/user/verify/contact", "/user/update/contact",
            "/actuator/health", "/error", "/deployed-hash", "/invite/validate", "/team-access/invitations/accept",
            "/sp/directory/", "/soko/catalog/", "/affiliate/public/");

    private final UserDao userDao;
    private final I18NService i18NService;
    private final ObjectMapper objectMapper;
    private final WorkspaceMembershipRepo workspaceMembershipRepo;

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
        var membershipRole = TeamMembershipRole.fromPlatformRole(activeRole);
        if (user != null && membershipRole.isPresent()
                && workspaceMembershipRepo.findFirstByUserIdAndMembershipRoleAndStatusAndActiveTrue(
                user.getId(), membershipRole.get(), TeamMembershipStatus.ACTIVE).isEmpty()) {
            deny(response, ResponseCode.FORBIDDEN_ACCESS,
                    "This workspace membership is not active. Ask the workspace owner to review your access.", user);
            return;
        }
        if (user != null && (AccountStatus.ACTIVE.name().equals(user.getAccountStatus())
                || INTERNAL_ROLES.contains(activeRole))) {
            filterChain.doFilter(request, response);
            return;
        }

        deny(response, ResponseCode.KYC_ACCOUNT_RESTRICTED,
                "Complete identity verification before using this feature.", user);
    }

    private void deny(HttpServletResponse response, ResponseCode code, String fallback, Object userObject) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        String description = i18NService.getLocalizedMessage(code);
        if (description == null || description.isBlank()) {
            description = fallback;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (userObject instanceof org.pms.silverocean.database.pms.entities.Users user) data.put("accountStatus", user.getAccountStatus());
        if (code == ResponseCode.KYC_ACCOUNT_RESTRICTED) data.put("destination", "/kyc");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", code.getCode());
        body.put("description", description);
        body.put("data", data);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean allowed(String path) {
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
