package org.pms.silverocean.service.auth.roles;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Keeps the platform-owner identity separate from every customer and
 * operational identity. This is an authorization boundary, not a UI rule.
 */
@Service
@RequiredArgsConstructor
public class SuperadminRoleIsolationPolicy {
    private final UserRoleRepo userRoles;

    public void assertCanAssume(long userId, PMSRole requestedRole) {
        Set<Role> assigned = userRoles.findByUserId(userId);
        boolean hasSuperadmin = assigned.stream().anyMatch(this::isActiveSuperadmin);
        boolean hasAnotherRole = assigned.stream()
                .filter(Role::isActive)
                .map(role -> PMSRole.roleFromSavedName(role.getName()))
                .anyMatch(role -> role != PMSRole.SUPER_ADMIN);

        if ((hasSuperadmin && requestedRole != PMSRole.SUPER_ADMIN)
                || (requestedRole == PMSRole.SUPER_ADMIN && hasAnotherRole)) {
            throw new PMSCustomException(ResponseCode.SUPERADMIN_ROLE_ISOLATED);
        }
    }

    public Set<Role> effectiveRoles(Set<Role> assigned) {
        return assigned.stream().anyMatch(this::isActiveSuperadmin)
                ? assigned.stream().filter(this::isActiveSuperadmin).collect(java.util.stream.Collectors.toSet())
                : assigned;
    }

    private boolean isActiveSuperadmin(Role role) {
        return role.isActive() && PMSRole.roleFromSavedName(role.getName()) == PMSRole.SUPER_ADMIN;
    }
}
