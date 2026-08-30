package org.pms.silverocean.service.auth.roles;

import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.PermissionRepo;
import org.pms.silverocean.database.pms.RolePermissionRepo;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Permission;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.RolePermission;
import org.pms.silverocean.database.pms.entities.UserRole;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.wrappers.RoleWrapper;
import org.pms.silverocean.service.invites.InviteDao;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.estate.EstateService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.service.kyc.KycService;
import org.pms.silverocean.service.teamaccess.TeamAccessService;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RoleService {
    private final UserRoleRepo userRoleRepo;
    private final PermissionRepo permissionRepo;

    private final RoleRepo roleRepo;

    private final PropertyManagerService propertyManagerService;
    private final InviteDao inviteDao;
    private final UserDao userDao;
    private final RolePermissionRepo rolePermissionRepo;

    private final I18NService i18NService;

    private final AuditLogService auditLogService;
    private final KycService kycService;
    private final TeamAccessService teamAccessService;
    private final EstateService estateService;

    public RoleService(UserRoleRepo userRoleRepo, PermissionRepo permissionRepo, RoleRepo roleRepo, PropertyManagerService propertyManagerService, UserDao userDao, RolePermissionRepo rolePermissionRepo, I18NService i18NService, AuditLogService auditLogService, InviteDao inviteDao, KycService kycService, TeamAccessService teamAccessService, EstateService estateService) {
        this.userRoleRepo = userRoleRepo;
        this.permissionRepo = permissionRepo;
        this.roleRepo = roleRepo;
        this.propertyManagerService = propertyManagerService;
        this.userDao = userDao;
        this.rolePermissionRepo = rolePermissionRepo;
        this.i18NService = i18NService;
        this.auditLogService = auditLogService;
        this.inviteDao = inviteDao;
        this.kycService = kycService;
        this.teamAccessService = teamAccessService;
        this.estateService = estateService;
    }

    public ResponseDTO selfAssignRole(long roleId) {
        Users user = userDao.getUserObject();
        if (user == null) throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        if (!AccountStatus.ACTIVE.name().equals(user.getAccountStatus())) {
            throw new PMSCustomException(ResponseCode.KYC_ACCOUNT_RESTRICTED);
        }
        checkIfRoleIsValidAndSelfAssignable(roleId);
        if (userRoleRepo.findByUserIdAndRoleId(user.getId(), roleId) == 0) {
            UserRole userRole = saveUserRole(user.getId(), roleId);
            auditLogService.createAuditLog(userRole, org.pms.silverocean.service.auth.roles.enums.Permission.ASSIGN_ROLE);
        }
        boolean kycRequired = kycService.reopenForNewRoleRequirements();
        return new ResponseDTO(true, ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY),
                Map.of("roleId", roleId, "kycRequired", kycRequired));
    }

    public ResponseDTO listRoles(Pageable pageable) {
        Page<RoleWrapper> roles = roleRepo.findAllActive(pageable).map(role ->
                new RoleWrapper(role, permissionRepo.findByRoleId(role.getId()), null)
        );
        return new ResponseDTO(true, ResponseCode.ROLES_LIST.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.ROLES_LIST),
                roles.get().toList());
    }

    private Set<IdNameDescDTO> resolvePropertiesForRole(Long userId, Role role) {
        PMSRole pmsRole = PMSRole.roleFromSavedName(role.getName());
        if (PMSRole.SUPER_ADMIN.equals(pmsRole)) {
            return Set.of();
        }
        if (PMSRole.LANDLORD.equals(pmsRole)) {
            return userRoleRepo.findLandlordsProperty(userId);
        }

        if (PMSRole.TENANT.equals(pmsRole)) {
            return userRoleRepo.findTenantProperty(userId);
        }

        if (PMSRole.HOMEOWNER.equals(pmsRole)) {
            return userRoleRepo.findHomeownerProperty(userId);
        }

        return userRoleRepo.findStaffPropertyByUserIdAndRole(userId, pmsRole.name());
    }

    public boolean checkIfStaffInProperty(long userId, long unitId) {
        return userRoleRepo.checkIfStaffInProperty(userId, unitId).isPresent();
    }

    public Set<Users> getPropertyManagersDetailsByUnitId(long unitId) {
        return userRoleRepo.findStaffPropertyAndRoleName(unitId, PMSRole.PROPERTY_MANAGER.getName());
    }

    public Set<RoleWrapper> getPermissionsForUser(Long userId) {
        return userRoleRepo.findByUserId(userId)
                .stream().map(role -> new RoleWrapper(role, permissionRepo.findByRoleId(role.getId()), resolvePropertiesForRole(userId, role))
                ).collect(Collectors.toSet());
    }

    public Set<RoleWrapper> getPermissionsForUser(String userName) {
        Users userByEmail = getUserByEmail(userName).orElseThrow();

        return getPermissionsForUser(userByEmail.getId());
    }

    @Transactional
    public void saveUserAndAssignRoleOnRegistration(long roleId, Users user) {
        checkIfRoleIsValidAndSelfAssignable(roleId);
        Users savedUser = userDao.save(user);
        saveUserRole(savedUser.getId(), roleId);
    }


    @Transactional
    public void saveUserAndAssignRoleFromInvite(String inviteToken, Long roleId, Users user) {
        if (teamAccessService.isTeamToken(inviteToken)) {
            teamAccessService.registerInvitedUser(inviteToken, user);
            return;
        }
        Invite invite = inviteDao.getInviteByToken(inviteToken, true)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.EXPIRED_INVITE_LINK));

        validateInviteRecipient(invite, user);
        user.setInviteId(invite.getId());
        if (invite.getRoleId() == null) {
            checkIfRoleIsValidAndSelfAssignable(roleId);
            invite.setRoleId(roleId); //A marketing invite, the roleId is null. Set it to the selected self assign role
        }
        userDao.save(user);
        assignRoleFromInvite(invite, invite.getRoleId(), user);
    }

    private void checkIfRoleIsValidAndSelfAssignable(Long roleId) {
        Optional<Role> roleByID = roleRepo.findByIdAndActive(roleId);
        if (roleByID.isEmpty() || !roleByID.get().isActive() || !roleByID.get().isSelfAssignable()) {
            log.error("Role with id {} not found", roleId);
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
    }

    public ResponseDTO addRoleToLoggedInUserUsingInviteToken(String inviteToken) {
        Users user = userDao.getUserObject();
        if (user == null) {
            log.error("No logged in user found");
            throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        }
        return assignRoleFromInvite(inviteToken, user);
    }


    @Transactional
    public ResponseDTO assignRoleFromInvite(String inviteToken, Users user) {
        if (teamAccessService.isTeamToken(inviteToken)) {
            teamAccessService.accept(inviteToken);
            return new ResponseDTO(true, ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY));
        }
        Invite invite = inviteDao.getInviteByToken(inviteToken, true).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN));
        return assignRoleFromInvite(invite, null, user);
    }

    @Transactional
    public ResponseDTO assignRoleFromInvite(Invite invite, Long roleId, Users user) {
        validateInviteRecipient(invite, user);
        String assignorEmail = userDao.findById(invite.getCreatedBy()).map(Users::getEmail).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        ResponseDTO responseDTO = assignRole(invite.getRoleId() == null ? roleId : invite.getRoleId(), user.getEmail(), assignorEmail);
        if (responseDTO.isSuccess()) {
            invite.setVisits(invite.getVisits() + 1);
            if (InviteType.valueOf(invite.getType()).isExpiresAfterUse() || invite.getRoleId() != null) {
                invite.setActive(false);
            }
            inviteDao.updateInvite(invite);
            roleRepo.findById(invite.getRoleId() == null ? roleId : invite.getRoleId()).ifPresent(role -> {
                PMSRole pmsRole = PMSRole.roleFromSavedName(role.getName());
                if (!pmsRole.isSelfAssignable() && invite.getEntityId() != null) {
                    attachUserToEntity(invite, user.getId(), pmsRole);
                }
            });
        }
        return responseDTO;
    }

    private void validateInviteRecipient(Invite invite, Users user) {
        if (invite.getRecipient() != null && !invite.getRecipient().isBlank()
                && !invite.getRecipient().equalsIgnoreCase(user.getEmail())) {
            throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        }
    }

    private ResponseDTO assignRole(long roleId, String assigneeEmail, String assignorEmail) {
        Optional<Role> roleByID = roleRepo.findByIdAndActive(roleId);
        if (roleByID.isEmpty()) {
            return new ResponseDTO(false, ResponseCode.INVALID_ROLE.getCode(), i18NService.getLocalizedMessage(ResponseCode.INVALID_ROLE));
        }
        Role role = roleByID.get();
        if (assignorEmail.equals(assigneeEmail) && !role.isSelfAssignable()) {
            return new ResponseDTO(false, ResponseCode.ROLE_NOT_SELF_ASSIGNABLE.getCode(), i18NService.getLocalizedMessage(ResponseCode.ROLE_NOT_SELF_ASSIGNABLE));
        }
        Optional<Users> users = getUserByEmail(assigneeEmail);
        if (users.isEmpty()) {
            throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        }
        Users user = users.get();
        if (userRoleRepo.findByUserIdAndRoleId(user.getId(), role.getId()) > 0) {
            return new ResponseDTO(true, ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY));
        }
        UserRole userRole = saveUserRole(user.getId(), role.getId());
        auditLogService.createAuditLog(userRole, org.pms.silverocean.service.auth.roles.enums.Permission.ASSIGN_ROLE);
        return new ResponseDTO(true, ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), i18NService.getLocalizedMessage(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY));
    }

    private void attachUserToEntity(Invite invite, long userId, PMSRole pmsRole) {
        if (PMSRole.HOMEOWNER.equals(pmsRole)) {
            estateService.createOwnershipFromInvite(invite.getEntityId(), userId, invite.getCreatedBy());
        } else if (!PMSRole.TENANT.equals(pmsRole)) {
            propertyManagerService.addStaffToProperty(invite.getId(), userId, invite.getEntityId(), pmsRole);
        }
    }

    private UserRole saveUserRole(long userId, long roleId) {
        UserRole userRole = new UserRole(userId, roleId);
        return userRoleRepo.save(userRole);
    }


    public Optional<Role> getOrCreateRoleIfMissing(String name, String description, boolean self_assignable) {
        Optional<Role> roleByName = roleRepo.findByName(name);
        if (roleByName.isEmpty()) {
            Role role = new Role(name, description, self_assignable);
            role.setActive(true);
            roleRepo.save(role);
            return Optional.of(role);
        }
        return roleByName;
    }

    public Optional<Permission> getOrCreatePermissionIfMissing(String name) {
        Optional<Permission> permissionByName = permissionRepo.findByName(name);
        if (permissionByName.isEmpty()) {
            Permission permission = new Permission(name);
            permissionRepo.save(permission);
            return Optional.of(permission);
        }
        return permissionByName;
    }

    public void mapPermissionToRoleIfMissing(Permission permission, Role role) {
        if (rolePermissionRepo.findByRoleIdAndPermissionId(role.getId(), permission.getId()).isEmpty()) {
            log.info("Mapping role {} and permission {}", role.getId(), permission.getId());
            RolePermission rolePermission = new RolePermission(role.getId(), permission.getId());
            rolePermissionRepo.save(rolePermission);
        }
    }

    private Optional<Users> getUserByEmail(String email) {
        try {
            return userDao.findByEmail(email);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
