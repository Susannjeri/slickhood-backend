package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.estate.EstateAccessService;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.property.PropertyService;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeownerInvitationTest {
    final InviteDao invites = mock(InviteDao.class);
    final PropertyService properties = mock(PropertyService.class);
    final UserDao users = mock(UserDao.class);
    final ConfigService config = mock(ConfigService.class);
    final RoleRepo roles = mock(RoleRepo.class);
    final NotificationService notifications = mock(NotificationService.class);
    final I18NService i18n = mock(I18NService.class);
    final EstateAccessService access = mock(EstateAccessService.class);
    final UnitDTO unit = mock(UnitDTO.class);
    final InviteService service = new InviteService(invites,properties,users,config,roles,notifications,i18n,null,null,access,null);

    void setupUnit(PMSLeaseMode mode) {
        when(users.getUserId()).thenReturn(9L);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS)).thenReturn(() -> new ConfigDTO(1,"expiry",null,7,false));
        when(properties.listUnits(any(),eq(Optional.empty()),eq(Optional.empty()),eq(Optional.of(77L)),eq(Optional.empty())))
                .thenReturn(new ResponseDTO(true,"ok","ok",unit));
        when(unit.propertyId()).thenReturn(11L); when(unit.leaseMode()).thenReturn(mode);
    }
    @Test void authorizedDelegateCanSendAnEmailBoundOneTimeHomeownerInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_URL)).thenReturn(() -> new ConfigDTO(2,"url","https://app.slickhood.test/invite",0,false));
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Accept your invitation: %s");
        Role homeowner = new Role(); homeowner.setId(6L); when(roles.findByName("Homeowner")).thenReturn(Optional.of(homeowner));
        AtomicReference<Invite> saved = new AtomicReference<>();
        doAnswer(call -> { Invite invite = call.getArgument(0); invite.setId(88L); saved.set(invite); return null; }).when(invites).createInvite(any());
        when(invites.getInviteByInviteIdAndCreatedBy(88L,9L)).thenAnswer(call -> Optional.of(saved.get()));
        service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L," Resident@Example.test ");
        verify(access).require(11L,Permission.MANAGE_ESTATE);
        assertEquals("resident@example.test",saved.get().getRecipient());
        assertEquals("HOMEOWNER",saved.get().getType());
        assertEquals(77L,saved.get().getEntityId());
        var notification = org.mockito.ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notifications).queueNotification(notification.capture());
        assertEquals("resident@example.test",notification.getValue().recipient());
        verify(properties,never()).getUnitByIDAndLoggedInUser(anyLong());
    }
    @Test void aRentalCannotReceiveAHomeownerInvitation() {
        setupUnit(PMSLeaseMode.RENT);
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L,"resident@example.test"));
        verifyNoInteractions(invites,notifications);
    }
    @Test void permissionDenialCannotSendOrCreateAnInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(access.require(11L,Permission.MANAGE_ESTATE)).thenThrow(new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L,"resident@example.test"));
        verifyNoInteractions(invites,notifications);
    }
}
