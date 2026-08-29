package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface PermissionRepo extends JpaRepository<Permission, Long> {
    @Query("SELECT p.name FROM Permission p JOIN RolePermission rp ON rp.permissionId=p.id WHERE rp.roleId=:roleId")
    Set<String> findByRoleId(Long roleId);

    Optional<Permission> findByName(String name);
}
