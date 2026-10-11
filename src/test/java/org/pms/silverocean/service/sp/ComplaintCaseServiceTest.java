package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
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
        if (java.util.Set.of(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(),
                BookingStatus.COMPLETED.name()).contains(status)) {
            b.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusHours(1));
        }
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
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_FOUND_whenBookingNotOwnedByUser() {
        // The locked lookup hides bookings that do not belong to the customer.
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_ACTIVE_whenBookingCancelled() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.CANCELLED.name(), true);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_ACTIVE, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_BOOKING_NOT_ACTIVE_whenBookingInactive() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.CONFIRMED.name(), false);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Bad service")));
        assertEquals(ResponseCode.SP_BOOKING_NOT_ACTIVE, ex.getResponseCode());
    }

    @Test
    void fileComplaint_throwsSP_COMPLAINT_ALREADY_EXISTS_whenBookingAlreadyHasComplaint() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.COMPLETED.name(), true);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsComplaintForBooking(10L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.fileComplaint(new FileComplaintRequest(10L, "Still bad")));
        assertEquals(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void fileComplaint_savesComplaintAndReturnsEnrichedDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(10L, 5L, 1L, BookingStatus.COMPLETED.name(), true);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(10L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsComplaintForBooking(10L)).thenReturn(false);
        doAnswer(inv -> { ((ComplaintCase) inv.getArgument(0)).setId(1L); return null; })
                .when(complaintDao).save(any(ComplaintCase.class), anyString());

        // toDTO lookups
        ProviderService ps = makeProviderService(5L, 20L, "Plumbing");
        ps.setCreatedBy(20L);
        when(serviceDao.findById(5L)).thenReturn(Optional.of(ps));
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Alice Tenant")));
        when(userDao.findById(20L)).thenReturn(Optional.of(makeUser(20L, "Bob Provider")));
        ProviderProfile providerProfile = new ProviderProfile();
        providerProfile.setId(20L);
        providerProfile.setUserId(20L);
        providerProfile.setBusinessName("Reliable Plumbing Ltd");
        when(profileDao.findById(20L)).thenReturn(Optional.of(providerProfile));
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
        org.junit.jupiter.api.Assertions.assertNull(dto.filedByUserId());
        org.junit.jupiter.api.Assertions.assertNull(dto.complaintCreatorName());
        assertEquals("Reliable Plumbing Ltd", dto.serviceCreatorName());
    }

    @Test
    void fileComplaint_isAvailableWhileCustomerIsReviewingProviderCompletion() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(11L, 5L, 1L,
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), true);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(11L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsComplaintForBooking(11L)).thenReturn(false);
        doAnswer(inv -> { ((ComplaintCase) inv.getArgument(0)).setId(2L); return null; })
                .when(complaintDao).save(any(ComplaintCase.class), anyString());

        ComplaintCaseDTO result = service.fileComplaint(
                new FileComplaintRequest(11L, "The submitted work is incomplete"));

        assertEquals(ComplaintStatus.OPEN.name(), result.status());
        assertEquals(11L, result.bookingId());
    }

    @Test
    void fileComplaint_rejectsAwaitingConfirmationAfterFortyEightHourDeadline() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(12L, 5L, 1L,
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), true);
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusSeconds(1));
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(12L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.fileComplaint(
                new FileComplaintRequest(12L, "Attempted after the complaint window")));

        assertEquals(ResponseCode.SP_BOOKING_NOT_ACTIVE, ex.getResponseCode());
        verify(complaintDao, never()).save(any(), anyString());
    }

    @Test
    void nonAdminComplaintListRedactsUserIdsAndPersonalNamesButKeepsProviderBusinessName() {
        ComplaintCase complaint = makeComplaint(21L, 5L, 10L, ComplaintStatus.OPEN.name());
        complaint.setDescription("The work was not completed.");
        when(userDao.getUserId()).thenReturn(1L);
        when(complaintDao.findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(any(), eq(5L), eq(1L)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(complaint)));
        ProviderService listing = makeProviderService(5L, 20L, "Plumbing");
        when(serviceDao.findById(5L)).thenReturn(Optional.of(listing));
        ProviderProfile providerProfile = new ProviderProfile();
        providerProfile.setBusinessName("Reliable Plumbing Ltd");
        when(profileDao.findById(20L)).thenReturn(Optional.of(providerProfile));
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Alice Tenant")));

        ComplaintCaseDTO result = service.listComplaintsForService(
                5L, org.springframework.data.domain.PageRequest.of(0, 20)).getContent().get(0);

        assertNull(result.filedByUserId());
        assertNull(result.assignedAdminId());
        assertNull(result.adminNotes());
        assertNull(result.complaintCreatorName());
        assertEquals("Reliable Plumbing Ltd", result.serviceCreatorName());
    }

    @Test
    void adminComplaintListUsesProviderBusinessNameAndMayRetainReviewerPartyMetadata() {
        ComplaintCase complaint = makeComplaint(22L, 5L, 10L, ComplaintStatus.OPEN.name());
        complaint.setAssignedAdminId(90L);
        when(complaintDao.findOpenAndUnderReview(any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(complaint)));
        ProviderService listing = makeProviderService(5L, 20L, "Plumbing");
        when(serviceDao.findById(5L)).thenReturn(Optional.of(listing));
        ProviderProfile providerProfile = new ProviderProfile();
        providerProfile.setBusinessName("Reliable Plumbing Ltd");
        when(profileDao.findById(20L)).thenReturn(Optional.of(providerProfile));
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Alice Tenant")));

        ComplaintCaseDTO result = service.listOpenComplaints(
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent().get(0);

        assertEquals(1L, result.filedByUserId());
        assertEquals(90L, result.assignedAdminId());
        assertEquals("Alice Tenant", result.complaintCreatorName());
        assertEquals("Reliable Plumbing Ltd", result.serviceCreatorName());
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
        ProviderService listing = makeProviderService(10L, 20L, "Electrical");
        ProviderProfile providerProfile = new ProviderProfile();
        providerProfile.setId(20L);
        providerProfile.setAdminNotes("Provider-visible onboarding note");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findById(20L)).thenReturn(Optional.of(providerProfile));
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Complaint for %s resolved: %s");

        service.resolveComplaint(61L, new ReviewComplaintRequest(ComplaintResolutionAction.WARN, "Minor infraction", null));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        ComplaintCase saved = captor.getValue();
        assertEquals(ComplaintStatus.RESOLVED.name(), saved.getStatus());
        assertEquals("WARNED", saved.getResolution());
        assertEquals("Provider-visible onboarding note", providerProfile.getAdminNotes());
        verify(profileDao, never()).save(eq(providerProfile), anyString());
    }

    @Test
    void resolveComplaint_suspendServiceUpholdsCaseAndBlacklistsAtFraudThreshold() {
        ComplaintCase complaint = makeComplaint(63L, 10L, 4L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(63L)).thenReturn(Optional.of(complaint));
        ProviderService listing = makeProviderService(10L, 20L, "Electrical");
        listing.setTitle("Domestic electrical repair");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        when(complaintDao.countUpheldComplaintsForProfile(20L)).thenReturn(3);
        ProviderProfile profile = new ProviderProfile();
        profile.setId(20L);
        profile.setUserId(30L);
        profile.setStatus("ACTIVE");
        when(profileDao.findByIdForUpdate(20L)).thenReturn(Optional.of(profile));
        ServiceBooking booking = makeBooking(4L, 10L, 4L,
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), true);
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("NOT_REQUIRED");
        when(bookingDao.findByIdForUpdate(4L)).thenReturn(Optional.of(booking));

        service.resolveComplaint(63L, new ReviewComplaintRequest(
                ComplaintResolutionAction.SUSPEND_SERVICE,
                "Internal evidence confirms serious misconduct and a linked fraud review",
                "The complaint was upheld and this service was suspended for breaching marketplace standards."));

        assertEquals(ComplaintStatus.RESOLVED.name(), complaint.getStatus());
        assertEquals("UPHELD", complaint.getResolution());
        verify(providerServiceService).suspendService(10L,
                "The complaint was upheld and this service was suspended for breaching marketplace standards.");
        assertEquals("The complaint was upheld and this service was suspended for breaching marketplace standards.",
                complaint.getPublicResolution());
        verify(riskScoreService).resetToUnderReview(10L);
        assertEquals("REQUESTED", booking.getRefundStatus());
        verify(bookingDao).save(booking, "SP_UPHELD_COMPLAINT_REFUND_REQUESTED");
        assertEquals("BLACKLISTED", profile.getStatus());
        verify(profileDao).save(profile, "blacklist_sp");
        org.mockito.InOrder thresholdOrder = org.mockito.Mockito.inOrder(profileDao, complaintDao);
        thresholdOrder.verify(profileDao).findByIdForUpdate(20L);
        thresholdOrder.verify(complaintDao).countUpheldComplaintsForProfile(20L);
    }

    @Test
    void resolveComplaint_doesNotCommitResolutionWhenFraudEnforcementFails() {
        ComplaintCase complaint = makeComplaint(64L, 10L, 4L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(64L)).thenReturn(Optional.of(complaint));
        ProviderService listing = makeProviderService(10L, 20L, "Electrical");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        ProviderProfile profile = new ProviderProfile();
        profile.setId(20L);
        profile.setStatus("ACTIVE");
        when(profileDao.findByIdForUpdate(20L)).thenReturn(Optional.of(profile));
        ServiceBooking booking = makeBooking(4L, 10L, 4L,
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), true);
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("NOT_REQUIRED");
        when(bookingDao.findByIdForUpdate(4L)).thenReturn(Optional.of(booking));
        when(complaintDao.countUpheldComplaintsForProfile(20L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.resolveComplaint(64L,
                new ReviewComplaintRequest(ComplaintResolutionAction.SUSPEND_SERVICE,
                        "Internal evidence", null)));

        verify(complaintDao, never()).save(any(), anyString());
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void resolveComplaint_ESCALATE_setsStatusESCALATED() {
        ComplaintCase c = makeComplaint(62L, 10L, 3L, ComplaintStatus.UNDER_REVIEW.name());
        when(complaintDao.findByIdForUpdate(62L)).thenReturn(Optional.of(c));
        doNothing().when(complaintDao).save(any(ComplaintCase.class), anyString());
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Complaint for %s resolved: %s");

        service.resolveComplaint(62L, new ReviewComplaintRequest(ComplaintResolutionAction.ESCALATE, "Needs senior review", null));

        ArgumentCaptor<ComplaintCase> captor = ArgumentCaptor.forClass(ComplaintCase.class);
        verify(complaintDao).save(captor.capture(), anyString());
        assertEquals(ComplaintStatus.ESCALATED.name(), captor.getValue().getStatus());
        org.mockito.Mockito.verifyNoInteractions(notificationService);
    }
}
