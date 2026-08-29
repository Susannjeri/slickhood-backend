package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolePermissionRepo extends JpaRepository<RolePermission, Long> {
    Optional<RolePermission> findByRoleIdAndPermissionId(long roleId, long permissionId);
}
