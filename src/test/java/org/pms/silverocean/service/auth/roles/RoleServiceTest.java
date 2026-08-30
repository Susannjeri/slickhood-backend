package org.pms.silverocean.service.auth.roles;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.PermissionRepo;
import org.pms.silverocean.database.pms.RolePermissionRepo;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.UserRoleRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.UserRole;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.PropertyManagerService;
import org.pms.silverocean.service.invites.InviteDao;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.estate.EstateService;
import org.pms.silverocean.service.subscription.SubscriptionProvisioningService;
import org.pms.silverocean.service.kyc.AccountStatus;
import org.pms.silverocean.service.kyc.KycService;
import org.pms.silverocean.service.teamaccess.TeamAccessService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private UserRoleRepo userRoleRepo;

    @Mock
    private PermissionRepo permissionRepo;

    @Mock
    private I18NService i18NService;

    @Mock
    RolePermissionRepo rolePermissionRep;
    @Mock
    AuditLogService auditLogService;

    @Mock
    private RoleRepo roleRepo;

    @Mock
    private UserDao userDao;

    @Mock
    private InviteDao inviteDao;

    @Mock
    private PropertyManagerService propertyManagerService;

    @Mock
    private SubscriptionProvisioningService subscriptionProvisioningService;

    @Mock
    private KycService kycService;

    @Mock
    private TeamAccessService teamAccessService;

    @Mock
    private EstateService estateService;

    @InjectMocks
    private RoleService roleService;

    private Users testUser;
    private Role testRole;
    private Role testRole3;

    @BeforeEach
    void setUp() {
        testUser = new Users();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testRole = new Role(PMSRole.LANDLORD.getName(), PMSRole.LANDLORD.getDescription(), true);
        testRole.setId(1L);
        testRole.setActive(true);

        testRole3 = new Role(PMSRole.PROPERTY_MANAGER.getName(), PMSRole.PROPERTY_MANAGER.getDescription(), false);
        testRole3.setId(2L);
        testRole3.setActive(true);
    }

    @Test
    void assignRoleFromInvite_existingUserGetsAdditionalRole_success() {


        // Mock invite
        Invite invite = new Invite();
        invite.setId(10L);
        invite.setRoleId(testRole3.getId()); // PROPERTY_MANAGER
        invite.setCreatedBy(999L);
        invite.setType(InviteType.PROPERTY_MANAGER.name());
        invite.setActive(true);

        // Mock assignor and assignee
        Users assignor = new Users();
        assignor.setId(999L);
        assignor.setEmail("assignor@example.com");

        Users existingUser = new Users();
        existingUser.setId(1L);
        existingUser.setEmail("test@example.com");


        when(userDao.findById(999L)).thenReturn(Optional.of(assignor));
        when(roleRepo.findByIdAndActive(testRole3.getId())).thenReturn(Optional.of(testRole3));
        when(userDao.findByEmail(existingUser.getEmail())).thenReturn(Optional.of(existingUser));


        when(userRoleRepo.findByUserIdAndRoleId(existingUser.getId(), testRole3.getId())).thenReturn(0);

        // Act
        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, existingUser);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), response.getCode());

        // Verify that a new role (SERVICE_PROVIDER) was saved
        verify(userRoleRepo, times(1)).save(any(UserRole.class));

        // Ensure existing role was not modified
        verify(userRoleRepo, never()).delete(any(UserRole.class));
        verify(userRoleRepo, never()).save(new UserRole(existingUser.getId(), testRole.getId())); // existing one untouched
    }


    @Test
    void assignRoleFromInvite_validInvite_assignsRoleSuccessfully() {

        Invite invite = new Invite();
        invite.setId(1L);
        invite.setRoleId(testRole3.getId());
        invite.setCreatedBy(100L);
        invite.setType(InviteType.PROPERTY_MANAGER.name());
        invite.setActive(true);

        Users assignor = new Users();
        assignor.setId(100L);
        assignor.setEmail("assignor@example.com");

        Users assignee = new Users();
        assignee.setId(200L);
        assignee.setEmail("assignee@example.com");


        when(userDao.findById(100L)).thenReturn(Optional.of(assignor));
        when(roleRepo.findByIdAndActive(2L)).thenReturn(Optional.of(testRole3));
        when(userDao.findByEmail(assignee.getEmail())).thenReturn(Optional.of(assignee));
        when(userRoleRepo.findByUserIdAndRoleId(anyLong(), anyLong())).thenReturn(0);
        // Act
        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, assignee);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), response.getCode());
        verify(userRoleRepo).save(any(UserRole.class));
    }

    @Test
    void assignRoleFromInvite_homeowner_createsOwnershipWithoutStaffAccess() {
        Role homeownerRole = new Role(PMSRole.HOMEOWNER.getName(), PMSRole.HOMEOWNER.getDescription(), false);
        homeownerRole.setId(12L);
        homeownerRole.setActive(true);
        Invite invite = new Invite();
        invite.setId(41L);
        invite.setRoleId(homeownerRole.getId());
        invite.setCreatedBy(999L);
        invite.setEntityId(77L);
        invite.setType(InviteType.HOMEOWNER.name());
        invite.setActive(true);
        Users assignor = new Users();
        assignor.setId(999L);
        assignor.setEmail("manager@example.com");
        Users homeowner = new Users();
        homeowner.setId(200L);
        homeowner.setEmail("owner@example.com");

        when(userDao.findById(999L)).thenReturn(Optional.of(assignor));
        when(roleRepo.findByIdAndActive(homeownerRole.getId())).thenReturn(Optional.of(homeownerRole));
        when(roleRepo.findById(homeownerRole.getId())).thenReturn(Optional.of(homeownerRole));
        when(userDao.findByEmail(homeowner.getEmail())).thenReturn(Optional.of(homeowner));
        when(userRoleRepo.findByUserIdAndRoleId(homeowner.getId(), homeownerRole.getId())).thenReturn(0);

        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, homeowner);

        assertTrue(response.isSuccess());
        verify(estateService).createOwnershipFromInvite(77L, homeowner.getId(), assignor.getId());
        verify(propertyManagerService, never()).addStaffToProperty(anyLong(), anyLong(), anyLong(), any(PMSRole.class));
        assertFalse(invite.isActive());
    }


    @Test
    void assignRoleFromInvite_invalidToken_throwsException() {
        String invalidToken = "expiredToken";

        when(inviteDao.getInviteByToken(invalidToken, true)).thenReturn(Optional.empty());

        Users user = new Users();
        user.setEmail("user@example.com");

        PMSCustomException exception = assertThrows(
                PMSCustomException.class,
                () -> roleService.assignRoleFromInvite(invalidToken, user)
        );

        assertEquals(ResponseCode.INVALID_OR_EXPIRED_TOKEN, exception.getResponseCode());
    }


    @Test
    void assignRoleFromInvite_existingRole_noDuplicateAssignment() {
        // Arrange similar setup as above
        Invite invite = new Invite();
        invite.setRoleId(testRole.getId()); // same role as existing
        invite.setCreatedBy(1L);
        invite.setType(InviteType.USER.name());

        Users user = new Users();
        user.setId(2L);
        user.setEmail("test@example.com");

        when(roleRepo.findByIdAndActive(anyLong())).thenReturn(Optional.of(testRole));
        when(userDao.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(userDao.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRoleRepo.findByUserIdAndRoleId(user.getId(), testRole.getId())).thenReturn(1);

        // Act
        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, user);
        // Assert
        assertTrue(response.isSuccess());
        verify(userRoleRepo, never()).save(any(UserRole.class));
    }

    @Test
    void assignRoleFromInvite_emailBoundInvite_rejectsDifferentUser() {
        Invite invite = new Invite();
        invite.setRecipient("intended@example.com");
        Users forwardedLinkUser = new Users();
        forwardedLinkUser.setEmail("different@example.com");

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> roleService.assignRoleFromInvite(invite, null, forwardedLinkUser));

        assertEquals(ResponseCode.INVALID_USER_DETAILS, exception.getResponseCode());
        verify(userRoleRepo, never()).save(any(UserRole.class));
    }

    @Test
    void registrationWithForwardedStaffInvite_rejectsBeforeSavingUser() {
        Invite invite = new Invite();
        invite.setRecipient("intended@example.com");
        when(inviteDao.getInviteByToken("staff-token", true)).thenReturn(Optional.of(invite));
        Users forwardedLinkUser = new Users();
        forwardedLinkUser.setEmail("different@example.com");

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> roleService.saveUserAndAssignRoleFromInvite("staff-token", null, forwardedLinkUser));

        assertEquals(ResponseCode.INVALID_USER_DETAILS, exception.getResponseCode());
        verify(userDao, never()).save(any(Users.class));
    }

    @Test
    void assignRoleFromInvite_internalStaffInvite_isOneTimeAndNotPropertyScoped() {
        Role supportRole = new Role(PMSRole.SUPPORT.getName(), PMSRole.SUPPORT.getDescription(), false);
        supportRole.setId(9L);
        supportRole.setActive(true);
        Invite invite = new Invite();
        invite.setId(30L);
        invite.setRoleId(supportRole.getId());
        invite.setCreatedBy(999L);
        invite.setType(InviteType.USER.name());
        invite.setRecipient("staff@example.com");
        invite.setActive(true);
        Users assignor = new Users();
        assignor.setId(999L);
        assignor.setEmail("admin@example.com");
        Users staff = new Users();
        staff.setId(50L);
        staff.setEmail("staff@example.com");

        when(userDao.findById(999L)).thenReturn(Optional.of(assignor));
        when(roleRepo.findByIdAndActive(9L)).thenReturn(Optional.of(supportRole));
        when(roleRepo.findById(9L)).thenReturn(Optional.of(supportRole));
        when(userDao.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
        when(userRoleRepo.findByUserIdAndRoleId(staff.getId(), supportRole.getId())).thenReturn(0);

        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, staff);

        assertTrue(response.isSuccess());
        assertFalse(invite.isActive());
        verify(inviteDao).updateInvite(invite);
        verify(propertyManagerService, never()).addStaffToProperty(anyLong(), anyLong(), anyLong(), any(PMSRole.class));
    }

    @Test
    void assignRoleFromInvite_nonSelfAssignableRole_failure() {

        Invite invite = new Invite();
        invite.setId(20L);
        invite.setRoleId(testRole3.getId()); // PROPERTY_MANAGER, self-assignable = false
        invite.setCreatedBy(1L);
        invite.setType(InviteType.PROPERTY_MANAGER.name());
        invite.setActive(true);

        Users selfUser = new Users();
        selfUser.setId(1L);
        selfUser.setEmail("selfuser@example.com");

        when(userDao.findById(1L)).thenReturn(Optional.of(selfUser));
        when(roleRepo.findByIdAndActive(testRole3.getId())).thenReturn(Optional.of(testRole3)); // self-assignable = false

        // Act
        ResponseDTO response = roleService.assignRoleFromInvite(invite, null, selfUser);

        // Assert
        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.ROLE_NOT_SELF_ASSIGNABLE.getCode(), response.getCode());
        verify(userRoleRepo, never()).save(any(UserRole.class));
    }

    @Test
    void assignRoleFromInvite_selfAssignableRole_success() {


        Users selfUser = new Users();
        selfUser.setId(1L);
        selfUser.setEmail("selfuser@example.com");

        // Mock dependencies
        when(roleRepo.findByIdAndActive(testRole.getId())).thenReturn(Optional.of(testRole)); // self-assignable = true
        when(userDao.save(selfUser)).thenReturn(selfUser);

        // Act
        roleService.saveUserAndAssignRoleOnRegistration(testRole.getId(), selfUser);

        // Assert
        verify(userRoleRepo, times(1)).save(any(UserRole.class));
    }



    @Test
    void saveUserAndAssignRoleOnRegistration_validRole_success() throws Exception {
        // Arrange
        when(roleRepo.findByIdAndActive(anyLong())).thenReturn(Optional.of(testRole));
        when(userDao.save(any(Users.class))).thenReturn(testUser);

        // Act
        assertDoesNotThrow(() -> roleService.saveUserAndAssignRoleOnRegistration(testRole.getId(), testUser));

        // Assert
        verify(userDao, times(1)).save(testUser);
        verify(userRoleRepo, times(1)).save(any(UserRole.class));
    }

    @Test
    void selfAssignRole_activeUser_addsOnlySelfAssignableRoleAndRechecksKyc() {
        testUser.setAccountStatus(AccountStatus.ACTIVE.name());
        when(userDao.getUserObject()).thenReturn(testUser);
        when(roleRepo.findByIdAndActive(testRole.getId())).thenReturn(Optional.of(testRole));
        when(userRoleRepo.findByUserIdAndRoleId(testUser.getId(), testRole.getId())).thenReturn(0);
        when(kycService.reopenForNewRoleRequirements()).thenReturn(true);

        ResponseDTO response = roleService.selfAssignRole(testRole.getId());

        assertTrue(response.isSuccess());
        assertEquals(true, ((java.util.Map<?, ?>) response.getData().getFirst()).get("kycRequired"));
        verify(userRoleRepo).save(any(UserRole.class));
        verify(kycService).reopenForNewRoleRequirements();
    }

    @Test
    void selfAssignRole_pendingKyc_isRejectedWithoutAddingRole() {
        testUser.setAccountStatus(AccountStatus.PENDING_KYC.name());
        when(userDao.getUserObject()).thenReturn(testUser);

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> roleService.selfAssignRole(testRole.getId()));

        assertEquals(ResponseCode.KYC_ACCOUNT_RESTRICTED, exception.getResponseCode());
        verify(userRoleRepo, never()).save(any(UserRole.class));
        verify(kycService, never()).reopenForNewRoleRequirements();
    }

    @Test
    void selfAssignRole_nonSelfAssignableRole_isRejected() {
        testUser.setAccountStatus(AccountStatus.ACTIVE.name());
        when(userDao.getUserObject()).thenReturn(testUser);
        when(roleRepo.findByIdAndActive(testRole3.getId())).thenReturn(Optional.of(testRole3));

        PMSCustomException exception = assertThrows(PMSCustomException.class,
                () -> roleService.selfAssignRole(testRole3.getId()));

        assertEquals(ResponseCode.INVALID_ROLE, exception.getResponseCode());
        verify(userRoleRepo, never()).save(any(UserRole.class));
    }

    @Test
    void getOrCreateRole_roleDoesNotExist_roleIfMissingCreated() {
        // Arrange
        String roleName = PMSRole.SERVICE_PROVIDER.getName();
        String description = PMSRole.SERVICE_PROVIDER.getDescription();
        boolean selfAssignable = true;
        when(roleRepo.findByName(anyString())).thenReturn(Optional.empty());

        // Act
        Optional<Role> createdRole = roleService.getOrCreateRoleIfMissing(roleName, description, selfAssignable);

        // Assert
        assertTrue(createdRole.isPresent());
        assertEquals(PMSUtils.stripNonLetters(roleName), createdRole.get().getName());
        verify(roleRepo, times(1)).save(any(Role.class));
    }

}
