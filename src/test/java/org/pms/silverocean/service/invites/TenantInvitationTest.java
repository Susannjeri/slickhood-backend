package org.pms.silverocean.service.invites;

import org.junit.jupiter.api.Test;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.RoleRepo;
import org.pms.silverocean.database.pms.LeaseDocumentTemplateRepo;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.LeaseDocumentTemplate;
import org.pms.silverocean.database.pms.entities.Role;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.property.PropertyService;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.leasedocument.DocumentTemplateIntegrity;
import org.pms.silverocean.service.leasedocument.LeaseDocumentType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantInvitationTest {
    final InviteDao invites = mock(InviteDao.class);
    final PropertyService properties = mock(PropertyService.class);
    final UserDao users = mock(UserDao.class);
    final ConfigService config = mock(ConfigService.class);
    final RoleRepo roles = mock(RoleRepo.class);
    final NotificationService notifications = mock(NotificationService.class);
    final I18NService i18n = mock(I18NService.class);
    final UnitDTO unit = mock(UnitDTO.class);
    final LeaseDocumentTemplateRepo templates = mock(LeaseDocumentTemplateRepo.class);
    final InviteService service = new InviteService(invites,properties,users,config,roles,notifications,i18n,null,null,null,templates,null);

    void unit(PMSLeaseMode mode, boolean occupied) {
        when(users.getUserId()).thenReturn(9L);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_EXPIRY_DAYS)).thenReturn(() -> new ConfigDTO(1,"expiry",null,7,false));
        when(properties.getUnitByIDAndLoggedInUser(77L)).thenReturn(new ResponseDTO(true,"ok","ok",unit));
        when(unit.leaseMode()).thenReturn(mode); when(unit.occupied()).thenReturn(occupied);
        when(unit.templateId()).thenReturn(1L);
        LeaseDocumentTemplate agreement = new LeaseDocumentTemplate();
        agreement.setId(21L); agreement.setDocumentType(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT);
        agreement.setBodyHtml("<html><body>Approved</body></html>");
        agreement.setContentSha256(DocumentTemplateIntegrity.sha256(agreement.getBodyHtml()));
        agreement.setLegalReviewRequired(false); agreement.setLegalReviewedAt(LocalDateTime.now()); agreement.setActive(true);
        when(templates.findFirstByDocumentTypeAndActiveTrueOrderByVersionDesc(LeaseDocumentType.RESIDENTIAL_LEASE_AGREEMENT))
                .thenReturn(Optional.of(agreement));
    }

    @Test void landlordInvitationBindsEmailAndQueuesDelivery() {
        unit(PMSLeaseMode.RENT,false);
        when(config.getConfigByName(PMSConfigs.INVITE_LINK_URL)).thenReturn(() -> new ConfigDTO(2,"url","https://app.slickhood.test/invite",0,false));
        when(i18n.getLocalizedMessage(anyString())).thenReturn("Open your unit invitation: %s");
        Role tenant = new Role(); tenant.setId(6L); when(roles.findByName("Tenant")).thenReturn(Optional.of(tenant));
        AtomicReference<Invite> saved = new AtomicReference<>();
        doAnswer(call -> { Invite invite = call.getArgument(0); invite.setId(88L); saved.set(invite); return null; }).when(invites).createInvite(any());
        when(invites.getInviteByInviteIdAndCreatedBy(88L,9L)).thenAnswer(call -> Optional.of(saved.get()));
        LocalDate start = LocalDate.now().plusDays(2), end = start.plusYears(1);
        service.createAndSendEmailInvite(InviteType.TENANT,77L," Tenant@Example.test ",start,end);
        assertEquals("tenant@example.test",saved.get().getRecipient());
        assertEquals("TENANT",saved.get().getType());
        assertEquals(77L,saved.get().getEntityId());
        assertEquals(start,saved.get().getLeaseStartDate());
        assertEquals(end,saved.get().getLeaseEndDate());
        assertEquals(21L,saved.get().getAgreementTemplateId());
        var notification = org.mockito.ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notifications).queueNotification(notification.capture());
        assertEquals("tenant@example.test",notification.getValue().recipient());
    }

    @Test void tenantAssignmentRequiresLandlordDefinedLeasePeriod() {
        unit(PMSLeaseMode.RENT,false);
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(
                InviteType.TENANT,77L,"tenant@example.test",null,null));
        verifyNoInteractions(invites,notifications);
    }

    @Test void saleAndEstateUnitsCannotSendRentalInvitations() {
        for (var mode : java.util.List.of(PMSLeaseMode.SALE,PMSLeaseMode.SERVICE_CHARGE)) {
            unit(mode,false);
            assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.TENANT,77L,"tenant@example.test"));
        }
        verifyNoInteractions(invites,notifications);
    }

    @Test void occupiedRentalCannotInviteANewTenant() {
        unit(PMSLeaseMode.RENT,true);
        assertThrows(PMSCustomException.class,()->service.createAndSendEmailInvite(InviteType.TENANT,77L,"tenant@example.test"));
        verifyNoInteractions(invites,notifications);
    }

    @Test void verifiedExistingTenantCanDiscoverPendingInvitationsWithoutTheEmailLink() {
        Users tenant = Users.builder().email("tenant@example.test").build();
        tenant.setActive(true);
        tenant.setEmailVerified(true);
        when(users.getUserObject()).thenReturn(tenant);

        PendingTenantInviteProjection newest = mock(PendingTenantInviteProjection.class);
        when(newest.getInviteId()).thenReturn(92L);
        when(newest.getToken()).thenReturn("new-token");
        when(newest.getUnitId()).thenReturn(77L);
        when(newest.getUnitRef()).thenReturn("A-101");
        when(newest.getPropertyName()).thenReturn("Acacia Court");
        when(newest.getLeaseStartDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(newest.getLeaseEndDate()).thenReturn(LocalDate.of(2027, 9, 30));
        when(newest.getExpiryDate()).thenReturn(LocalDateTime.of(2026, 9, 22, 12, 0));

        PendingTenantInviteProjection olderForSameUnit = mock(PendingTenantInviteProjection.class);
        when(olderForSameUnit.getUnitId()).thenReturn(77L);
        when(invites.listPendingTenantInvites("tenant@example.test"))
                .thenReturn(List.of(newest, olderForSameUnit));

        var pending = service.getPendingTenantInvitesForCurrentUser();

        assertEquals(1, pending.size());
        assertEquals(92L, pending.getFirst().inviteId());
        assertEquals("new-token", pending.getFirst().token());
        assertEquals("Acacia Court", pending.getFirst().propertyName());
    }

    @Test void unverifiedAccountCannotDiscoverEmailBoundInvitations() {
        Users tenant = Users.builder().email("tenant@example.test").build();
        tenant.setActive(true);
        tenant.setEmailVerified(false);
        when(users.getUserObject()).thenReturn(tenant);

        assertThrows(PMSCustomException.class, service::getPendingTenantInvitesForCurrentUser);
        verify(invites, never()).listPendingTenantInvites(anyString());
    }
}
