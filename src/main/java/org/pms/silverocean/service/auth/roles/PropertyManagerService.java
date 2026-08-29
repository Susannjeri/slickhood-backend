package org.pms.silverocean.service.auth.roles;

import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.database.pms.PropertyManagerRepo;
import org.pms.silverocean.database.pms.UnitTenantRepo;
import org.pms.silverocean.database.pms.entities.PropertyManager;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.wrappers.PropertyStaff;
import org.pms.silverocean.service.auth.roles.wrappers.StaffProjection;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.invites.InviteDTO;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.property.PropertyDao;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PropertyManagerService {
    private final PropertyManagerRepo propertyManagerRepo;
    private final UnitTenantRepo unitTenantRepo;
    private final AuditLogService auditLogService;
    private final ConfigService configService;
    private final PropertyDao propertyDao;
    private final UserDao userDao;


    public PropertyManagerService(PropertyManagerRepo propertyManagerRepo, UnitTenantRepo unitTenantRepo,
                                  AuditLogService auditLogService, ConfigService configService,
                                  PropertyDao propertyDao, UserDao userDao) {
        this.propertyManagerRepo = propertyManagerRepo;
        this.unitTenantRepo = unitTenantRepo;
        this.auditLogService = auditLogService;
        this.configService = configService;
        this.propertyDao = propertyDao;
        this.userDao = userDao;
    }


    public void addStaffToProperty(long inviteId, long userId, long propertyId, PMSRole role) {
        Optional<PropertyManager> existingPermission = propertyManagerRepo.findByUserIdAndPropertyIdAndRoleNameAndActiveTrue(userId, propertyId, role.name());
        if (existingPermission.isEmpty()) {
            PropertyManager propertyManager = new PropertyManager();
            propertyManager.setUserId(userId);
            propertyManager.setPropertyId(propertyId);
            propertyManager.setRoleName(role.name());
            propertyManager.setInviteId(inviteId);
            propertyManager.setActive(true);


            propertyManagerRepo.save(propertyManager);
            auditLogService.createAuditLog(propertyManager, "add_" + role.name().toLowerCase());
        }
    }

//    public void addTenantToProperty(long inviteId, long userId, long unitId) {
//        Optional<UnitTenant> existingTenancy = unitTenantRepo.findByUserIdAndUnitIdAndActiveTrue(userId, unitId);
//        if (existingTenancy.isEmpty()) {
//            UnitTenant unitTenant = new UnitTenant();
//            unitTenant.setUserId(userId);
//            unitTenant.setUnitId(unitId);
//            unitTenant.setInviteId(inviteId);
//            unitTenant.setActive(true);
//
//
//            unitTenantRepo.save(unitTenant);
//            auditLogService.createAuditLog(unitTenant, "new_tenant");
//        }
//    }

    public Optional<String> getStaffRoleInProperty(long userId, long propertyId) {
        return propertyManagerRepo.findRoleNameByUserIdAndPropertyIdAndActiveTrue(userId, propertyId);
    }

    public void removeStaffFromProperty(long staffId) {
        PropertyManager propertyManager = propertyManagerRepo.findById(staffId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        requirePropertyAccess(propertyManager.getPropertyId());
        propertyManager.setActive(false);
        propertyManagerRepo.save(propertyManager);
        auditLogService.createAuditLog(propertyManager, "remove_" + propertyManager.getRoleName().toLowerCase());
    }

    public PropertyStaff findAllStaffByProperty(long propertyId) {
        requirePropertyAccess(propertyId);
        List<StaffProjection> staff = propertyManagerRepo.findByPropertyIdAndActiveTrue(propertyId);
        String inviteURL = configService.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue();
        List<InviteDTO> pendingInviteList = propertyManagerRepo.findPendingStaffInviteByProperty(propertyId, InviteType.TENANT.name())
                .stream().map(invite -> new InviteDTO(invite, PMSUtils.formatInviteLink(inviteURL, invite.getToken()), Duration.between(LocalDateTime.now(), invite.getExpiryDate()).toDays())).toList();
        return new PropertyStaff(staff, pendingInviteList);
    }

    private void requirePropertyAccess(long propertyId) {
        if (propertyDao.findByIdAndStaffOrOwner(propertyId, userDao.getUserId()).isEmpty()) {
            throw new PMSCustomException(ResponseCode.PROPERTY_FORBIDDEN_ACCESS);
        }
    }
}
