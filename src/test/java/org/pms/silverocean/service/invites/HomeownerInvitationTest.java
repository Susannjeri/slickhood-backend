package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.PropertyAccountRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
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
import org.pms.silverocean.service.property.wrappers.UnitLifecycleDTO;
import org.pms.silverocean.service.teamaccess.TeamAccessService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.pms.silverocean.service.leasedocument.DocumentTemplateIntegrity;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;
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
    final LeaseDocumentTemplateRepo templates = mock(LeaseDocumentTemplateRepo.class);
    final PropertyAccountRepo accounts = mock(PropertyAccountRepo.class);
    final RoleService roleService = mock(RoleService.class);
    final TeamAccessService teamAccess = mock(TeamAccessService.class);
    final InviteService service = new InviteService(invites,properties,users,config,roles,notifications,i18n,roleService,teamAccess,access,templates,accounts);

    void setupUnit(PMSLeaseMode mode) {
        when(users.getUserId()).thenReturn(9L);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS)).thenReturn(() -> new ConfigDTO(1,"expiry",null,7,false));
        when(properties.listUnits(any(),eq(Optional.empty()),eq(Optional.empty()),eq(Optional.of(77L)),eq(Optional.empty())))
                .thenReturn(new ResponseDTO(true,"ok","ok",unit));
        when(unit.propertyId()).thenReturn(11L); when(unit.leaseMode()).thenReturn(mode);
        if (mode == PMSLeaseMode.SERVICE_CHARGE) {
            LeaseDocumentTemplate template = new LeaseDocumentTemplate();
            template.setId(41L); template.setActive(true); template.setVersion(1);
            template.setDocumentType(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT);
            template.setBodyHtml("<html><body>{{propertyName}}</body></html>");
            template.setContentSha256(DocumentTemplateIntegrity.sha256(template.getBodyHtml()));
            template.setLegalReviewRequired(false); template.setLegalReviewedAt(java.time.LocalDateTime.now());
            when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.ESTATE_RESIDENTIAL_AGREEMENT)).thenReturn(Optional.of(template));
            when(accounts.countVerifiedOperatingAccounts(11L, org.pms.silverocean.service.account.enums.AccountCategory.ESTATE_MANAGEMENT)).thenReturn(1L);
        }
    }
    @Test void authorizedDelegateCanSendAnEmailBoundOneTimeHomeownerInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_URL)).thenReturn(() -> new ConfigDTO(2,"url","https://app.slickhood.test/invite",0,false));
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Accept your invitation: %s");
        Role homeowner = new Role(); homeowner.setId(6L); when(roles.findByName("Homeowner")).thenReturn(Optional.of(homeowner));
        AtomicReference<Invite> saved = new AtomicReference<>();
        doAnswer(call -> { Invite invite = call.getArgument(0); invite.setId(88L); saved.set(invite); return null; }).when(invites).createInvite(any());
        when(invites.getInviteByInviteIdAndCreatedBy(88L,9L)).thenAnswer(call -> Optional.of(saved.get()));
        service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L," Resident@Example.test ", LocalDate.now(), null);
        verify(access).require(11L,Permission.MANAGE_ESTATE);
        assertEquals("resident@example.test",saved.get().getRecipient());
        assertEquals("HOMEOWNER",saved.get().getType());
        assertEquals(77L,saved.get().getEntityId());
        assertEquals(LocalDate.now(),saved.get().getLeaseStartDate());
        assertEquals(41L,saved.get().getAgreementTemplateId());
        verify(notifications).queueEmailAndInApp(eq("resident@example.test"),any(),contains("Accept your invitation:"),eq("INVITE_RECEIVED"),contains("homeowner invitation"));
        verify(properties,never()).getUnitByIDAndLoggedInUser(anyLong());
    }
    @Test void aRentalCannotReceiveAHomeownerInvitation() {
        setupUnit(PMSLeaseMode.RENT);
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L,"resident@example.test", LocalDate.now(), null));
        verifyNoInteractions(invites,notifications);
    }
    @Test void permissionDenialCannotSendOrCreateAnInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(access.require(11L,Permission.MANAGE_ESTATE)).thenThrow(new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L,"resident@example.test", LocalDate.now(), null));
        verifyNoInteractions(invites,notifications);
    }
    @Test void homeownerInvitationRequiresAConfiguredEstateReceivingAccount() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(accounts.countVerifiedOperatingAccounts(11L, org.pms.silverocean.service.account.enums.AccountCategory.ESTATE_MANAGEMENT)).thenReturn(0L);

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.createAndSendEmailInvite(InviteType.HOMEOWNER,77L,"resident@example.test", LocalDate.now(), null));

        assertEquals(ResponseCode.ESTATE_RECEIVING_ACCOUNT_REQUIRED, error.getResponseCode());
        verifyNoInteractions(invites, notifications);
    }

    @Test void homeownerInvitationExplainsAnInvalidAgreementDateSeparately() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.createAndSendEmailInvite(InviteType.HOMEOWNER, 77L,
                        "resident@example.test", LocalDate.now(org.pms.silverocean.common.PMSUtils.getZoneId()).plusDays(1), null));

        assertEquals(ResponseCode.HOMEOWNER_AGREEMENT_DATE_INVALID, error.getResponseCode());
        verifyNoInteractions(invites, notifications);
    }

    @Test void activeHomeownerJourneyMustBeCancelledBeforeAnotherInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        when(unit.lifecycle()).thenReturn(new UnitLifecycleDTO(
                "HOMEOWNER_INVITED", "Homeowner invited", "An invitation is active.", true, 88L, null));

        PMSCustomException error = assertThrows(PMSCustomException.class, () ->
                service.createAndSendEmailInvite(InviteType.HOMEOWNER, 77L,
                        "other@example.test", LocalDate.now(), null));

        assertEquals(ResponseCode.INVITE_ALREADY_EXISTS, error.getResponseCode());
        verify(properties).lockUnitForInvitation(77L);
        verifyNoInteractions(invites, notifications);
    }

    @Test void authorizedEstateManagerCanCancelAColleaguesActiveHomeownerInvitation() {
        setupUnit(PMSLeaseMode.SERVICE_CHARGE);
        Invite invite = new Invite();
        invite.setId(88L);
        invite.setCreatedBy(7L);
        invite.setEntityId(77L);
        invite.setType(InviteType.HOMEOWNER.name());
        invite.setActive(true);
        when(invites.getInviteByInviteIdAndCreatedBy(88L, 9L)).thenReturn(Optional.empty());
        when(invites.getActiveInviteById(88L)).thenReturn(Optional.of(invite));

        service.updateInvite(88L, false);

        assertFalse(invite.isActive());
        verify(access).require(11L, Permission.MANAGE_ESTATE);
        verify(invites).updateInvite(invite);
    }

    @Test void consumedHomeownerInvitationRemainsUsableOnlyForItsAuthenticatedRecipient() {
        Invite invite = consumedHomeownerInvite("resident@example.test");
        Users recipient = new Users();
        recipient.setId(51L);
        recipient.setEmail("Resident@Example.test");
        recipient.setActive(true);
        recipient.setEmailVerified(true);
        when(teamAccess.isTeamToken("already-used")).thenReturn(false);
        when(invites.getInviteByToken("already-used", true)).thenReturn(Optional.empty());
        when(invites.getInviteByToken("already-used", false)).thenReturn(Optional.of(invite));
        when(users.getUserObject()).thenReturn(recipient);
        when(users.getUserId()).thenReturn(recipient.getId());
        when(i18n.getLocalizedMessage(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY)).thenReturn("Role ready");

        ResponseDTO response = service.validateToken("already-used");

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.ROLE_ASSIGNED_SUCCESSFULLY.getCode(), response.getCode());
        assertEquals("/dashboard/documents?type=ESTATE_RESIDENTIAL_AGREEMENT", response.getData().getFirst());
        verifyNoInteractions(roleService);
    }

    @Test void consumedHomeownerInvitationCannotBeReusedByAnotherAccount() {
        Invite invite = consumedHomeownerInvite("resident@example.test");
        Users other = new Users();
        other.setId(52L);
        other.setEmail("other@example.test");
        other.setActive(true);
        other.setEmailVerified(true);
        when(teamAccess.isTeamToken("already-used")).thenReturn(false);
        when(invites.getInviteByToken("already-used", true)).thenReturn(Optional.empty());
        when(invites.getInviteByToken("already-used", false)).thenReturn(Optional.of(invite));
        when(users.getUserObject()).thenReturn(other);
        when(users.getUserId()).thenReturn(other.getId());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.validateToken("already-used"));

        assertEquals(ResponseCode.INVALID_INVITE_LINK, error.getResponseCode());
        verifyNoInteractions(roleService);
    }

    @Test void loggedOutRecipientOfConsumedHomeownerInvitationIsSentToSignIn() {
        Invite invite = consumedHomeownerInvite("resident@example.test");
        when(teamAccess.isTeamToken("already-used")).thenReturn(false);
        when(invites.getInviteByToken("already-used", true)).thenReturn(Optional.empty());
        when(invites.getInviteByToken("already-used", false)).thenReturn(Optional.of(invite));
        when(users.getUserObject()).thenReturn(null);
        when(users.getUserId()).thenReturn(null);
        when(config.getConfigByName(PMSConfigs.REGISTRATION_PAGE_URL)).thenReturn(
                () -> new ConfigDTO(3, "registration", "https://app.slickhood.test/register", 0, false));

        ResponseDTO response = service.validateToken("already-used");

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.ASSIGNED_ROLE_REGISTRATION_REQUIRED.getCode(), response.getCode());
        assertTrue(response.getData().getFirst().toString().contains("already-used"));
        verifyNoInteractions(roleService);
    }

    private Invite consumedHomeownerInvite(String recipient) {
        Invite invite = new Invite();
        invite.setType(InviteType.HOMEOWNER.name());
        invite.setRecipient(recipient);
        invite.setToken("already-used");
        invite.setVisits(1);
        invite.setActive(false);
        invite.setExpiryDate(LocalDateTime.now().plusDays(1));
        return invite;
    }
}
