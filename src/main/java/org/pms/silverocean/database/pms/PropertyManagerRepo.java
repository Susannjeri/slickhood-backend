package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.PropertyManager;
import org.pms.silverocean.service.auth.roles.wrappers.StaffProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PropertyManagerRepo extends JpaRepository<PropertyManager, Long> {
    Optional<PropertyManager> findByUserIdAndPropertyIdAndRoleNameAndActiveTrue(long userId, long propertyId, String roleName);
    @Query("SELECT pm.roleName FROM PropertyManager pm WHERE pm.userId=:userId AND pm.propertyId=:propertyId")
    Optional<String> findRoleNameByUserIdAndPropertyIdAndActiveTrue(long userId, long propertyId);
    @Query("SELECT pm.id as staffId, u.email as email, u.fullName as name, pm.roleName as type, pm.createdOn as joinedOn FROM Users u JOIN PropertyManager pm ON u.id=pm.userId WHERE u.active AND pm.active AND pm.propertyId=:propertyId")
    List<StaffProjection> findByPropertyIdAndActiveTrue(long propertyId);

    @Query("SELECT i FROM Invite i JOIN Property p ON i.entityId=p.id WHERE p.id=:propertyId AND i.active AND i.type <> :tenantInvite")
    List<Invite> findPendingStaffInviteByProperty(long propertyId, String tenantInvite);
}
