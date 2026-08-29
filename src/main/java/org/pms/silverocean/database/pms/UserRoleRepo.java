package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.UserRole;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface UserRoleRepo extends JpaRepository<UserRole, Long> {
    @Query("SELECT r FROM UserRole ur JOIN Role r ON ur.roleId=r.id WHERE ur.userId=:userId")
    Set<Role> findByUserId(Long userId);

    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(p.id, p.name) FROM Property p WHERE p.createdBy=:userId and p.active")
    Set<IdNameDescDTO> findLandlordsProperty(Long userId);

    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(pm.propertyId, p.name)  FROM PropertyManager pm JOIN Property p ON  p.id=pm.propertyId WHERE pm.userId=:userId AND pm.roleName=:role AND pm.active AND p.active")
    Set<IdNameDescDTO> findStaffPropertyByUserIdAndRole(Long userId, String role);

    @Query("SELECT u FROM Users u JOIN PropertyManager pm on pm.userId=u.id JOIN Unit un ON pm.propertyId=un.propertyId WHERE un.id=:unitId AND pm.roleName=:roleName AND pm.active AND u.active AND un.active")
    Set<Users> findStaffPropertyAndRoleName(Long unitId, String roleName);

    @Query("SELECT pm.propertyId FROM PropertyManager pm JOIN Property p ON  p.id=pm.propertyId JOIN Unit u ON u.propertyId=p.id WHERE u.id=:unitId AND pm.userId=:userId AND pm.active AND p.active AND u.active")
    Optional<Long> checkIfStaffInProperty(Long userId, long unitId);



    @Query("SELECT new org.pms.silverocean.service.wrappers.IdNameDescDTO(u.propertyId, p.name)  FROM Unit u JOIN Property p ON p.id=u.propertyId JOIN UnitTenant ut ON u.id=ut.unitId WHERE ut.userId=:userId AND ut.active AND u.active")
    Set<IdNameDescDTO> findTenantProperty(Long userId);

    @Query("SELECT COUNT(ur) FROM UserRole ur JOIN Role r ON ur.roleId=r.id WHERE ur.roleId=:roleId AND ur.userId=:userId")
    int findByUserIdAndRoleId(Long userId, Long roleId);

}
