package org.pms.silverocean.service.auth.roles;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.database.pms.entities.Permission;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.auth.roles.enums.PMSPermission;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PermissionInit {
    private final RoleService roleService;

    public PermissionInit(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostConstruct
    private void init() {
        log.info("Setting up roles in database...");
        try {
            initPermissions();
            log.info("Roles in database initialized successfully.");
        } catch (Exception e) {
            log.error("Error while initializing roles in database.", e);
        }

    }

    public void initPermissions() {
        EnumSet<PMSPermission> permissions = EnumSet.allOf(PMSPermission.class);
        for (PMSPermission permissionsInRole : permissions) {
            PMSRole role = permissionsInRole.getRole();
            Optional<Role> dbRole = roleService.getOrCreateRoleIfMissing(role.getName(), role.getDescription(), role.isSelfAssignable());
            if (dbRole.isPresent()) {
                log.info("Role {} has {} permissions", role.getName(), permissionsInRole.getPermissions().size());
                Set<Permission> collectedPermissions = permissionsInRole.getPermissions().stream().map(roleService::getOrCreatePermissionIfMissing)
                        .filter(Optional::isPresent).map(Optional::get)
                        .collect(Collectors.toSet());
                log.info("{} permissions loaded from db for role {}", collectedPermissions.size(), role.getName());
                collectedPermissions.forEach(permission -> roleService.mapPermissionToRoleIfMissing(permission, dbRole.get()));
            }
        }
    }
}
