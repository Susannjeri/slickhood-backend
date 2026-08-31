package org.pms.silverocean.service.visitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.database.pms.VisitorAccessEventRepo;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest;
import org.pms.silverocean.service.visitor.wrappers.UpdateVisitorStatusRequest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class VisitorServiceTest {

    @Mock
    private VisitorDao visitorDao;
    @Mock
    private UserDao userDao;
    @Mock
    private NotificationService notificationService;
    @Mock
    private I18NService i18NService;

    @Mock
    private ConfigService configService;
    @Mock
    private VisitorAccessEventRepo accessEventRepo;

    private VisitorService visitorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);


        visitorService = new VisitorService(visitorDao, userDao, notificationService, i18NService, configService, accessEventRepo);

    }

    record UnitProjectionFixture(long getUnitId, long getPropertyId,
                                 String getUnitRef, String getPropertyName)
            implements PropertyIdUnitRefPropertyNameProjection {}

    @Test
    void preRegisterVisitor_throwsException_whenTenantNotFound() {
        when(userDao.getUserObject()).thenReturn(null);

        CreateVisitorRequest request = new CreateVisitorRequest(
                "John Doe", "KCA 123A", LocalDateTime.now().plusHours(2),
                "Slot A1", false, 10L, "+254712345678", VisitorCategory.GUEST
        );

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.preRegisterVisitor(request));
        assertEquals(ResponseCode.COULD_NOT_FIND_USER_SESSION, ex.getResponseCode());
    }

    @Test
    void preRegisterVisitor_savesVisitorAndSendsConfirmation_whenNotificationEnabled() {
        Users tenant = new Users();
        tenant.setId(1L);
        tenant.setEmail("test@test.com");
        when(userDao.getUserObject()).thenReturn(tenant);
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Visitor %s expected at %s.");


        ConfigDTO notificationEnabled =  new ConfigDTO(1L, PMSConfigs.VISITOR_NOTIFICATION_ENABLED.getName(), "true", 0, false);
        Supplier<ConfigDTO> visitorNotificationEnabled = () -> notificationEnabled;
        when(configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_ENABLED)).thenReturn(visitorNotificationEnabled);

        ConfigDTO notificationChannel =  new ConfigDTO(1L, PMSConfigs.VISITOR_NOTIFICATION_CHANNEL.getName(), "Email", 0, false);
        Supplier<ConfigDTO> visitorNotificationChannel = () -> notificationChannel;
        when(configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_CHANNEL)).thenReturn(visitorNotificationChannel);
        visitorService.init();

        UnitProjectionFixture unitProjection  = new UnitProjectionFixture(1L, 1L, "unit-Ref", "propertyName");

        when(visitorDao.checkIfTenantInUnit(anyLong(), anyLong())).thenReturn(Optional.of(unitProjection));

        CreateVisitorRequest request = new CreateVisitorRequest(
                "John Doe", "KCA 123A", LocalDateTime.now().plusHours(2),
                "Slot A1", false, 10L, "+254712345678", VisitorCategory.GUEST
        );

        visitorService.preRegisterVisitor(request);

        ArgumentCaptor<Visitor> visitorCaptor = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorDao).save(visitorCaptor.capture(), anyString());
        Visitor saved = visitorCaptor.getValue();
        assertEquals("John Doe", saved.getVisitorName());
        assertEquals(VisitorStatus.PENDING.name(), saved.getStatus());
        assertEquals(10L, saved.getUnitId());

        verify(notificationService, times(1)).sendNotification(any(NotificationDTO.class));
    }

    @Test
    void preRegisterVisitor_savesVisitorAndSkipsConfirmation_whenNotificationDisabled() {
        Users tenant = new Users();
        tenant.setId(1L);
        tenant.setEmail("test@test.com");
        when(userDao.getUserObject()).thenReturn(tenant);
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Visitor %s expected at %s.");

        ConfigDTO notificationEnabled =  new ConfigDTO(1L, PMSConfigs.VISITOR_NOTIFICATION_ENABLED.getName(), "false", 0, false);
        Supplier<ConfigDTO> visitorNotificationEnabled = () -> notificationEnabled;
        when(configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_ENABLED)).thenReturn(visitorNotificationEnabled);

        visitorService.init();

        UnitProjectionFixture unitProjection  = new UnitProjectionFixture(1L, 1L, "unit-Ref", "propertyName");

        when(visitorDao.checkIfTenantInUnit(anyLong(), anyLong())).thenReturn(Optional.of(unitProjection));


        CreateVisitorRequest request = new CreateVisitorRequest(
                "John Doe", "KCA 123A", LocalDateTime.now().plusHours(2),
                "Slot A1", false, 10L, "+254712345678", VisitorCategory.GUEST
        );

        visitorService.preRegisterVisitor(request);

        ArgumentCaptor<Visitor> visitorCaptor = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorDao).save(visitorCaptor.capture(), anyString());
        Visitor saved = visitorCaptor.getValue();
        assertEquals("John Doe", saved.getVisitorName());
        assertEquals(VisitorStatus.PENDING.name(), saved.getStatus());
        assertEquals(10L, saved.getUnitId());

        verify(notificationService, times(0)).sendNotification(any(NotificationDTO.class));
    }

    @Test
    void cancelVisitor_throwsException_whenVisitorNotOwnedByTenant() {
        when(userDao.getUserId()).thenReturn(1L);
        when(visitorDao.findByIdAndCreatedBy(anyLong(), anyLong())).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.cancelVisitor(42L));
        assertEquals(ResponseCode.VISITOR_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void listVisitorsByUnit_rejectsUserOutsideTargetProperty() {
        when(userDao.getUserId()).thenReturn(7L);
        when(visitorDao.canStaffOrOwnerAccessUnit(55L, 7L)).thenReturn(false);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.listVisitorsByUnit(PageRequest.of(0, 20), 55L, Optional.empty()));

        assertEquals(ResponseCode.UNIT_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void cancelVisitor_throwsException_whenVisitorAlreadyCheckedIn() {
        Visitor visitor = new Visitor();
        visitor.setStatus(VisitorStatus.CHECKED_IN.name());

        when(userDao.getUserId()).thenReturn(1L);
        when(visitorDao.findByIdAndCreatedBy(anyLong(), anyLong())).thenReturn(Optional.of(visitor));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.cancelVisitor(42L));
        assertEquals(ResponseCode.VISITOR_ALREADY_PROCESSED, ex.getResponseCode());
    }

    @Test
    void cancelVisitor_cancelsSuccessfully_whenVisitorIsPending() {
        Visitor visitor = new Visitor();
        visitor.setStatus(VisitorStatus.PENDING.name());

        when(userDao.getUserId()).thenReturn(1L);
        when(visitorDao.findByIdAndCreatedBy(anyLong(), anyLong())).thenReturn(Optional.of(visitor));

        visitorService.cancelVisitor(42L);

        ArgumentCaptor<Visitor> captor = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorDao).save(captor.capture(), anyString());
        assertEquals(VisitorStatus.CANCELLED.name(), captor.getValue().getStatus());
    }

    @Test
    void updateVisitorStatus_rejectsInvalidReentryData_whenCheckedOut() {
        Visitor visitor = new Visitor();
        visitor.setStatus(VisitorStatus.CHECKED_OUT.name());

        Users tenant = new Users();
        tenant.setId(1L);
        tenant.setEmail("test@test.com");

        when(userDao.getUserObject()).thenReturn(tenant);
        when(visitorDao.findByIdAndGuard(anyLong(), anyLong())).thenReturn(Optional.of(visitor));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.updateVisitorStatus(10L, VisitorStatus.CHECKED_IN));
        assertEquals(ResponseCode.VISITOR_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void updateVisitorStatus_updatesStatus_whenVisitorIsPending() {
        Visitor visitor = new Visitor();
        visitor.setStatus(VisitorStatus.PENDING.name());
        visitor.setExpectedArrivalTime(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusMinutes(5));
        visitor.setMaxEntries(1);

        Users guard = new Users();
        guard.setId(1L);
        guard.setEmail("test@test.com");

        when(userDao.getUserObject()).thenReturn(guard);

        when(visitorDao.findByIdAndGuard(anyLong(), anyLong())).thenReturn(Optional.of(visitor));

        visitorService.updateVisitorStatus(10L, VisitorStatus.CHECKED_IN);

        ArgumentCaptor<Visitor> captor = ArgumentCaptor.forClass(Visitor.class);
        verify(visitorDao).save(captor.capture(), anyString());
        assertEquals(VisitorStatus.CHECKED_IN.name(), captor.getValue().getStatus());
        verify(accessEventRepo).save(any());
    }

    @Test
    void invalidPhoneSearchReturnsNoRowsInsteadOfFallingBackToTheFullVisitorList() {
        when(userDao.getUserId()).thenReturn(1L);
        assertEquals(0, visitorService.listMyVisitors(PageRequest.of(0, 25), Optional.of("not-a-phone")).size());
        verify(visitorDao, never()).findByTenantOrGuardOrLandlordOrPropertyManager(any(), anyLong());
    }

    @Test
    void updateVisitorStatus_rejectsEntryBeforeHostApproval() {
        Visitor visitor = validVisitor(VisitorStatus.PENDING_APPROVAL);
        Users guard = new Users(); guard.setId(1L);
        when(userDao.getUserObject()).thenReturn(guard);
        when(visitorDao.findByIdAndGuard(10L, 1L)).thenReturn(Optional.of(visitor));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> visitorService.updateVisitorStatus(10L, VisitorStatus.CHECKED_IN));
        assertEquals(ResponseCode.VISITOR_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void updateVisitorStatus_enforcesWindowEntryLimitAndVehicleIdentity() {
        Users guard = new Users(); guard.setId(1L);
        when(userDao.getUserObject()).thenReturn(guard);

        Visitor expired = validVisitor(VisitorStatus.APPROVED);
        expired.setValidUntil(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        when(visitorDao.findByIdAndGuard(10L, 1L)).thenReturn(Optional.of(expired));
        assertThrows(PMSCustomException.class, () -> visitorService.updateVisitorStatus(10L, VisitorStatus.CHECKED_IN));

        Visitor wrongVehicle = validVisitor(VisitorStatus.APPROVED);
        wrongVehicle.setVisitType("DRIVE_IN"); wrongVehicle.setVehiclePlate("KDA123A");
        when(visitorDao.findByIdAndGuard(11L, 1L)).thenReturn(Optional.of(wrongVehicle));
        assertThrows(PMSCustomException.class, () -> visitorService.updateVisitorStatus(11L,
                new UpdateVisitorStatusRequest(VisitorStatus.CHECKED_IN, "KDB999B")));

        Visitor exhausted = validVisitor(VisitorStatus.CHECKED_OUT);
        exhausted.setEntryCount(1); exhausted.setMaxEntries(1);
        when(visitorDao.findByIdAndGuard(12L, 1L)).thenReturn(Optional.of(exhausted));
        assertThrows(PMSCustomException.class, () -> visitorService.updateVisitorStatus(12L, VisitorStatus.CHECKED_IN));
    }

    @Test
    void deleteVisitor_blocksAnActiveOccupantAndRedactsTerminalPii() {
        when(userDao.getUserId()).thenReturn(1L);
        Visitor inside = validVisitor(VisitorStatus.CHECKED_IN);
        when(visitorDao.findByIdAndCreatedBy(20L, 1L)).thenReturn(Optional.of(inside));
        assertThrows(PMSCustomException.class, () -> visitorService.deleteVisitor(20L));

        Visitor departed = validVisitor(VisitorStatus.CHECKED_OUT);
        departed.setVisitorName("Sensitive Name"); departed.setPhoneNumber("+254700000000");
        departed.setVehiclePlate("KDA123A"); departed.setCredentialHash("secret-hash");
        when(visitorDao.findByIdAndCreatedBy(21L, 1L)).thenReturn(Optional.of(departed));
        visitorService.deleteVisitor(21L);
        assertEquals("Deleted visitor", departed.getVisitorName());
        assertEquals(null, departed.getPhoneNumber());
        assertEquals(null, departed.getCredentialHash());
        assertEquals(false, departed.isActive());
    }

    private Visitor validVisitor(VisitorStatus status) {
        Visitor visitor = new Visitor();
        visitor.setId(10L); visitor.setPropertyId(5L); visitor.setStatus(status.name()); visitor.setActive(true);
        visitor.setExpectedArrivalTime(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusMinutes(5));
        visitor.setValidFrom(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(5));
        visitor.setValidUntil(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusMinutes(30));
        visitor.setMaxEntries(1);
        return visitor;
    }
}
