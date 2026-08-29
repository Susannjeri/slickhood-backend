package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.sp.dao.ComplaintCaseDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ComplaintResolutionAction;
import org.pms.silverocean.service.sp.enums.ComplaintStatus;
import org.pms.silverocean.service.sp.wrappers.ComplaintCaseDTO;
import org.pms.silverocean.service.sp.wrappers.FileComplaintRequest;
import org.pms.silverocean.service.sp.wrappers.ReviewComplaintRequest;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintCaseServiceTest {

    @Mock private ComplaintCaseDao complaintDao;
    @Mock private ProviderServiceDao serviceDao;
    @Mock private ProviderProfileDao profileDao;
    @Mock private ServiceBookingDao bookingDao;
    @Mock private ProviderServiceService providerServiceService;
    @Mock private RiskScoreService riskScoreService;
    @Mock private UserDao userDao;
    @Mock private NotificationService notificationService;
    @Mock private I18NService i18NService;
    @Mock private ConfigService configService;

    private ComplaintCaseService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ConfigDTO fraudLimitConfig = new ConfigDTO(1L, PMSConfigs.SP_FRAUD_FAILURE_LIMIT_VALUE.getName(), "3", 3, false);
        Supplier<ConfigDTO> fraudLimitSupplier = () -> fraudLimitConfig;
        when(configService.getConfigByName(PMSConfigs.SP_FRAUD_FAILURE_LIMIT_VALUE)).thenReturn(fraudLimitSupplier);

        service = new ComplaintCaseService(complaintDao, serviceDao, profileDao, bookingDao,
                providerServiceService, riskScoreService, userDao, notificationService, i18NService, configService);

        service.init();
    }

    private ServiceBooking makeBooking(long id, long serviceId, long bookedByUserId, String status, boolean active) {
        ServiceBooking b = new ServiceBooking();
        b.setId(id);
        b.setServiceId(serviceId);
        b.setCreatedBy(bookedByUserId);
        b.setStatus(status);
        b.setActive(active);
        return b;
    }

    private ProviderService makeProviderService(long id, long profileId, String categoryName) {
        ProviderService s = new ProviderService();
        s.setId(id);
        s.setProfileId(profileId);
        s.setCategoryName(categoryName);
        s.setStatus("LISTED");
        return s;
    }

    private ComplaintCase makeComplaint(long id, long serviceId, long bookingId, String status) {
        ComplaintCase c = new ComplaintCase();
        c.setId(id);
        c.setServiceId(serviceId);
        c.setBookingId(bookingId);
        c.setFiledByUserId(1L);
        c.setStatus(status);
        return c;
    }

    private Users makeUser(long id, String fullName) {
        Users u = new Users();
        u.setId(id);
        u.setFullName(fullName);
        u.setEmail(fullName.toLowerCase().replace(" ", ".") + "@test.com");
        return u;
    }

    // --- fileComplaint ---

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_FOUND_whenBookingAbsent() {
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_FOUND_whenBookingNotOwnedByUser() {
        // findByIdAndBookingCreatedByForRead returns empty when the booking belongs to another user
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_ACTIVE_whenBookingCancelled() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.CANCELLED.name(), true);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_ACTIVE, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_ACTIVE_whenBookingInactive() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.CONFIRMED.name(), false);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_ACTIVE, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_COMPLAINT_ALREADY_EXISTS_whenOpenComplaintExists() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.COMPLETED.name(), true);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsOpenComplaintForBooking(10L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Still bad")));
        assertEquals(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void fileComplaint_savesComplaintAndReturnsEnrichedDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.COMPLETED.name(), true);
        when(bookingDao.findByIdAndBookingCreatedByForRead(10L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsOpenComplaintForBooking(10L)).thenReturn(false);
        doAnswer(inv -> { ((ComplaintCase) inv.getArgument(0)).setId(1L); return null; })
                .when(complaintDao).save(any(ComplaintCase.class), anyString());

        // toDTO lookups
        ProviderService ps = makeProviderService(5L, 20L, "Plumbing");
        ps.setCreatedBy(20L);
        when(serviceDao.findById(5L)).thenReturn(Optional.of(ps));
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Alice Tenant")));
        when(userDao.findById(20L)).thenReturn(Optional.of(makeUser(20L, "Bob Provider")));
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("A complaint was filed for service %s");

        ComplaintCaseDTO dto = service.fileComplaint(new FileComplaintRequest(10L, "Provider was rude"));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        ComplaintCase saved = captor.getValue();

        assertEquals(ComplaintStatus.OPEN.name(), saved.getStatus());
        assertEquals(5L, saved.getServiceId());
        assertEquals(10L, saved.getBookingId());
        assertEquals(1L, saved.getFiledByUserId());
        assertEquals("Provider was rude", saved.getDescription());

        assertEquals(ComplaintStatus.OPEN.name(), dto.status());
        assertEquals(10L, dto.bookingId());
        assertEquals(5L, dto.serviceId());
        assertEquals("Plumbing", dto.serviceName());
        assertEquals("Alice Tenant", dto.complaintCreatorName());
        assertEquals("Bob Provider", dto.serviceCreatorName());
    }

    // --- assignReview ---

    @Test
    void assignReview_throwsSP_COMPLAINT_NOT_FOUND_whenComplaintAbsent() {
        when(userDao.getUserId()).thenReturn(1L);
        when(complaintDao.findByIdForUpdate(50L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.assignReview(50L));
        assertEquals(ResponseCode.SP_COMPLAINT_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void assignReview_throwsSP_COMPLAINT_CANNOT_REVIEW_whenNotOpen() {
        when(userDao.getUserId()).thenReturn(1L);
        ComplaintCase c = makeComplaint(50L, 10L, 1L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(50L)).thenReturn(Optional.of(c));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.assignReview(50L));
        assertEquals(ResponseCode.SP_COMPLAINT_CANNOT_REVIEW, ex.getResponseCode());
    }

    @Test
    void assignReview_setsStatusUNDER_REVIEW_onSuccess() {
        when(userDao.getUserId()).thenReturn(5L);
        ComplaintCase c = makeComplaint(50L, 10L, 1L, ComplaintStatus.OPEN.name());
        when(complaintDao.findByIdForUpdate(50L)).thenReturn(Optional.of(c));
        doNothing().when(complaintDao).save(any(ComplaintCase.class), anyString());

        service.assignReview(50L);

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        ComplaintCase saved = captor.getValue();
        assertEquals(ComplaintStatus.UNDER_REVIEW.name(), saved.getStatus());
        assertEquals(5L, saved.getAssignedAdminId());
    }

    // --- resolveComplaint ---

    @Test
    void resolveComplaint_DISMISS_throwsSP_COMPLAINT_NOT_FOUND_whenAbsent() {
        when(complaintDao.findByIdForUpdate(60L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.resolveComplaint(60L, new ReviewComplaintRequest(ComplaintResolutionAction.DISMISS, "No evidence", null)));
        assertEquals(ResponseCode.SP_COMPLAINT_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void resolveComplaint_DISMISS_setsStatusRESOLVED_andResolutionDISMISSED() {
        ComplaintCase c = makeComplaint(60L, 10L, 1L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(60L)).thenReturn(Optional.of(c));
        doNothing().when(complaintDao).save(any(ComplaintCase.class), anyString());
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Complaint for %s resolved: %s");

        service.resolveComplaint(60L, new ReviewComplaintRequest(ComplaintResolutionAction.DISMISS, "Insufficient evidence", null));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        ComplaintCase saved = captor.getValue();
        assertEquals(ComplaintStatus.RESOLVED.name(), saved.getStatus());
        assertEquals("DISMISSED", saved.getResolution());
        assertEquals("Insufficient evidence", saved.getAdminNotes());
    }

    @Test
    void resolveComplaint_WARN_setsStatusRESOLVED_andResolutionWARNED() {
        ComplaintCase c = makeComplaint(61L, 10L, 2L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(61L)).thenReturn(Optional.of(c));
        doNothing().when(complaintDao).save(any(ComplaintCase.class), anyString());
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Complaint for %s resolved: %s");

        service.resolveComplaint(61L, new ReviewComplaintRequest(ComplaintResolutionAction.WARN, "Minor infraction", null));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        ComplaintCase saved = captor.getValue();
        assertEquals(ComplaintStatus.RESOLVED.name(), saved.getStatus());
        assertEquals("WARNED", saved.getResolution());
    }

    @Test
    void resolveComplaint_ESCALATE_setsStatusESCALATED() {
        ComplaintCase c = makeComplaint(62L, 10L, 3L, ComplaintStatus.OPEN.name());
        when(complaintDao.findByIdForUpdate(62L)).thenReturn(Optional.of(c));
        doNothing().when(complaintDao).save(any(ComplaintCase.class), anyString());
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Complaint for %s resolved: %s");

        service.resolveComplaint(62L, new ReviewComplaintRequest(ComplaintResolutionAction.ESCALATE, "Needs senior review", null));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        assertEquals(ComplaintStatus.ESCALATED.name(), captor.getValue().getStatus());
    }
}
