package org.pms.silverocean.service.invites;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.pms.silverocean.service.property.PropertyService;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.users.StaffInviteDTO;
import org.pms.silverocean.service.users.StaffInviteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.pms.silverocean.common.PMSUtils.formatInviteLink;

@Service
public class InviteService {
    private static final Set<PMSRole> INTERNAL_STAFF_ROLES = Set.of(
            PMSRole.SUPPORT, PMSRole.SALES_MARKETING, PMSRole.FINANCE
    );
    private final InviteDao inviteDao;
    private final PropertyService propertyService;
    private final UserDao userDao;
    private final ConfigService configService;
    private final RoleRepo roleRepo;

    private final NotificationService notificationService;

    private final I18NService i18NService;
    private final RoleService roleService;


    public InviteService(InviteDao inviteDao, PropertyService propertyService, UserDao userDao, ConfigService configService, RoleRepo roleRepo, NotificationService notificationService, I18NService i18NService, RoleService roleService) {
        this.inviteDao = inviteDao;
        this.propertyService = propertyService;
        this.userDao = userDao;
        this.configService = configService;
        this.roleRepo = roleRepo;
        this.notificationService = notificationService;
        this.i18NService = i18NService;
        this.roleService = roleService;
    }

    public String createInviteLink(InviteType inviteType, Long entityId) {
        Invite invite = new Invite();
        invite.setCreatedBy(userDao.getUserId());
        invite.setToken(PMSUtils.randomMask());

        invite.setExpiryDate(LocalDateTime.now().plusDays(configService.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS).get().intValue()));
        invite.setActive(true);

        switch (inviteType) {
            case GUARD, PROPERTY_MANAGER, ASSET_PORTFOLIO_MANAGER, FINANCE -> {
                ResponseDTO propertyDTO = propertyService.getPropertyByIDAndLoggedInUser(entityId);
                if (!propertyDTO.isSuccess()) {
                    throw new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND);
                }
            }
            case TENANT -> {
                ResponseDTO responseDTO = propertyService.getUnitByIDAndLoggedInUser(entityId);
                if (!responseDTO.isSuccess()) {
                    throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
                }
                if (responseDTO.getData().get(0) instanceof UnitDTO unitDTO) {
                    if (unitDTO.templateId() == null) {
                        throw new PMSCustomException(ResponseCode.MISSING_LEASE_TEMPLATE);
                    }
                } else {
                    throw new PMSCustomException(ResponseCode.GENERAL_FAILURE);
                }
            }
        }

        invite.setEntityId(entityId);
        invite.setType(inviteType.name());
        invite.setRoleId(getRoleFromInviteType(inviteType));
        inviteDao.createInvite(invite);
        return formatInviteLink(configService.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue(), invite.getToken());
    }

    public StaffInviteDTO createInternalStaffInvite(StaffInviteRequest request) {
        if (!userDao.hasRole(PMSRole.SUPER_ADMIN)) {
            throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        }
        if (!INTERNAL_STAFF_ROLES.contains(request.role())) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }

        String recipient = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            InternetAddress emailAddr = new InternetAddress(recipient);
            emailAddr.validate();
        } catch (AddressException e) {
            throw new PMSCustomException(ResponseCode.INVALID_EMAIL);
        }

        Role role = roleRepo.findByName(request.role().getName())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_ROLE));
        Invite invite = new Invite();
        invite.setCreatedBy(userDao.getUserId());
        invite.setToken(PMSUtils.randomMask());
        invite.setExpiryDate(LocalDateTime.now().plusDays(
                configService.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS).get().intValue()));
        invite.setActive(true);
        invite.setType(InviteType.USER.name());
        invite.setRoleId(role.getId());
        invite.setRecipient(recipient);
        inviteDao.createInvite(invite);
        sendInvite(invite.getId(), recipient, NotificationChannel.EMAIL);
        return new StaffInviteDTO(recipient, displayName(request.role()), invite.getExpiryDate());
    }

    private String displayName(PMSRole role) {
        return switch (role) {
            case SALES_MARKETING -> "Sales & Marketing";
            default -> role.getName();
        };
    }

    public void sendInvite(long inviteId, String contact, NotificationChannel channel) {
        Optional<Invite> inviteFromDb = inviteDao.getInviteByInviteIdAndCreatedBy(inviteId, userDao.getUserId());
        if (inviteFromDb.isEmpty()) {
            throw new PMSCustomException(ResponseCode.INVALID_INVITE_LINK);
        }
        Invite invite = inviteFromDb.get();
        if (LocalDateTime.now().isAfter(invite.getExpiryDate())) {
            throw new PMSCustomException(ResponseCode.EXPIRED_INVITE_LINK);
        }
        InviteType inviteType = InviteType.valueOf(invite.getType());
        NotificationDTO notificationDTO = null;
        switch (channel) {
            case EMAIL -> {
                try {
                    InternetAddress emailAddr = new InternetAddress(contact);
                    emailAddr.validate();
                    String formattedMessage = String.format(i18NService.getLocalizedMessage(inviteType.getInviteEmail().getBody()), formatInviteLink(configService.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue(), invite.getToken()));
                    notificationDTO = new NotificationDTO(formattedMessage, contact, inviteType.getInviteEmail());
                } catch (AddressException e) {
                    throw new PMSCustomException(ResponseCode.INVALID_EMAIL);
                }
            }
            case SMS -> {
                if (PMSUtils.isPhoneInvalid(contact)) {
                    throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER);
                }
                String formattedMessage = String.format(i18NService.getLocalizedMessage(inviteType.getInviteSMS().getBody()), formatInviteLink(configService.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue(), invite.getToken()));
                notificationDTO = new NotificationDTO(formattedMessage, contact, inviteType.getInviteSMS());
            }
        }
        if (notificationDTO == null) {
            throw new PMSCustomException(ResponseCode.EXPIRED_INVITE_LINK);
        }
        notificationService.sendNotification(notificationDTO);
    }

    public ResponseDTO validateToken(String token) {
        Invite invite = inviteDao.getInviteByToken(token, true).filter(
                inviteFromDb -> LocalDateTime.now().isBefore(inviteFromDb.getExpiryDate())
        ).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVITE_LINK));

        InviteType inviteType = InviteType.valueOf(invite.getType());
        ResponseDTO responseDTO = null;
        switch (inviteType) {
            case GUARD, PROPERTY_MANAGER, ASSET_PORTFOLIO_MANAGER, FINANCE -> {
                if (userDao.getUserId() != null) {
                    //assign user role and ADD user to property
                    responseDTO = roleService.assignRoleFromInvite(invite, null, userDao.getUserObject());
                    if (responseDTO.isSuccess()) {
                        responseDTO.setData(List.of(configService.getConfigByName(PMSConfigs.HOME_PAGE_URL).get().stringValue()));
                    }
                } else {
                    //return user to registration page, registration process to handle assigning user role and property
                    responseDTO = new ResponseDTO(true, ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED.getCode(),
                            i18NService.getLocalizedMessage(ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED), formatInviteLink(configService.getConfigByName(PMSConfigs.REGISTRATION_PAGE_URL).get().stringValue(), token));
                }
            }
            case TENANT -> {
                //return unit for tenant to view
                responseDTO = propertyService.viewUnitLease(token);
                if (userDao.getUserId() != null && !userDao.hasRole(PMSRole.TENANT)) {
                    roleService.assignRoleFromInvite(invite, null, userDao.getUserObject());
                }
            }
            case USER -> {
                if (invite.getRoleId() != null && userDao.getUserId() != null) {
                    responseDTO = roleService.assignRoleFromInvite(invite, null, userDao.getUserObject());
                    if (responseDTO.isSuccess()) {
                        responseDTO.setData(List.of(configService.getConfigByName(PMSConfigs.HOME_PAGE_URL).get().stringValue()));
                    }
                } else if (invite.getRoleId() != null) {
                    responseDTO = new ResponseDTO(true, ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED.getCode(),
                            i18NService.getLocalizedMessage(ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED),
                            formatInviteLink(configService.getConfigByName(PMSConfigs.REGISTRATION_PAGE_URL).get().stringValue(), token));
                } else if (userDao.getUserId() != null) {
                    responseDTO = new ResponseDTO(true, ResponseCode.HOME_PAGE.getCode(), ResponseCode.HOME_PAGE.getDescription(), configService.getConfigByName(PMSConfigs.HOME_PAGE_URL).get().stringValue());
                } else {
                    //return user to registration page
                    responseDTO = new ResponseDTO(true, ResponseCode.SELF_ASSIGN_ROLE_REGISTRATION_REQUIRED.getCode(),
                            i18NService.getLocalizedMessage(ResponseCode.SELF_ASSIGN_ROLE_REGISTRATION_REQUIRED), formatInviteLink(configService.getConfigByName(PMSConfigs.ROLE_ASSIGN_PAGE_URL).get().stringValue(), token));
                }
            }
        }
        return responseDTO;
    }

    public Page<InviteDTO> getUserInvites(Pageable pageable, Long unitId) {
        Page<Invite> invites;
        if (unitId != null) {
            invites = inviteDao.listInvitesByUnitAndCreatedBy(pageable, userDao.getUserId(), unitId);
        } else {
            invites = inviteDao.listUserInvites(pageable, userDao.getUserId());
        }
        return invites.map(invite -> new InviteDTO(invite, formatInviteLink(configService.getConfigByName(PMSConfigs.INVITE_LINK_URL).get().stringValue(), invite.getToken()), Duration.between(LocalDateTime.now(), invite.getExpiryDate()).toDays()));
    }

    public void updateInvite(long id, boolean active) {
        Optional<Invite> inviteFromDb = inviteDao.getInviteByInviteIdAndCreatedBy(id, userDao.getUserId());
        if (inviteFromDb.isEmpty()) {
            return;
        }
        Invite invite = inviteFromDb.get();
        invite.setActive(active);
        if (active) {
            invite.setExpiryDate(LocalDateTime.now().plusDays(configService.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS).get().intValue()));
        }
        inviteDao.updateInvite(invite);
    }

    public ResponseDTO getSupportedInviteTypes() {
        List<EnumWrapper> inviteTypes = EnumSet.allOf(InviteType.class).stream()
                .map(inviteType -> new EnumWrapper(inviteType.name(), i18NService.getLocalizedMessage(inviteType.getName()), null))
                .collect(Collectors.toList());
        return new ResponseDTO(true, ResponseCode.INVITE_TYPES.getCode(),
                i18NService.getLocalizedMessage(ResponseCode.INVITE_TYPES), inviteTypes);
    }

    private Long getRoleFromInviteType(InviteType inviteType) {
        if (inviteType == InviteType.USER) {
            return null;
        }
        Role role = roleRepo.findByName(PMSRole.valueOf(inviteType.name()).getName())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVITE_TYPE));
        return role.getId();
    }


}
