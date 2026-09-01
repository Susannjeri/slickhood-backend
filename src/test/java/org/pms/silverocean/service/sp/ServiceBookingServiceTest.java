package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.payment.invoice.InvoiceDao;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.CreateBookingRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceBookingDTO;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceBookingServiceTest {

    @Mock
    private ServiceBookingDao bookingDao;
    @Mock
    private ProviderServiceDao serviceDao;
    @Mock
    private ProviderProfileDao profileDao;
    @Mock
    private UserDao userDao;
    @Mock
    private NotificationService notificationService;
    @Mock
    private I18NService i18NService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private InvoiceDao invoiceDao;
    @Mock
    private UnitDao unitDao;

    private ServiceBookingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceBookingService(bookingDao, serviceDao, profileDao, userDao,
                notificationService, i18NService, accountDao, invoiceDao, unitDao);
    }

    private ProviderService makeProviderService(long id, long profileId, String status, String categoryName) {
        ProviderService s = new ProviderService();
        s.setId(id);
        s.setProfileId(profileId);
        s.setStatus(status);
        s.setCategoryName(categoryName);
        return s;
    }

    private ServiceBooking makeBooking(long id, long serviceId, String status) {
        ServiceBooking b = new ServiceBooking();
        b.setId(id);
        b.setServiceId(serviceId);
        b.setCreatedBy(1L);
        b.setStatus(status);
        b.setQuotedAmount(new BigDecimal("1000.00"));
        b.setCurrency("KES");
        b.setScheduledAt(LocalDateTime.now().plusDays(1).atZone(ZoneId.of("UTC")));
        return b;
    }

    private ProviderProfile makeProfile(long id, String businessName) {
        ProviderProfile p = new ProviderProfile();
        p.setId(id);
        p.setBusinessName(businessName);
        p.setUserId(99L);
        return p;
    }

    private Users makeUser(long id, String fullName, String email) {
        Users u = new Users();
        u.setId(id);
        u.setFullName(fullName);
        u.setEmail(email);
        return u;
    }

    // --- createBooking ---

    @Test
    void createBooking_throwsSP_SERVICE_NOT_FOUND_whenServiceAbsent() {
        when(userDao.getUserId()).thenReturn(1L);
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), "notes", null, null);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.createBooking(request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createBooking_throwsSP_SERVICE_NOT_FOUND_whenServiceNotListed() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.SUBMITTED.name(), "Cleaning");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(ps));

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), "notes", null, null);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.createBooking(request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createBooking_savesBookingWithPendingStatus_andReturnsPopulatedDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(ps));
        doAnswer(inv -> { ((ServiceBooking) inv.getArgument(0)).setId(1L); return null; })
                .when(bookingDao).save(any(ServiceBooking.class), anyString());

        ProviderProfile profile = makeProfile(5L, "CleanCo");
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));

        Users booker = makeUser(1L, "Alice Tenant", "alice@test.com");
        when(userDao.findById(1L)).thenReturn(Optional.of(booker));

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), "Please call first", null, null);

        ServiceBookingDTO dto = service.createBooking(request);

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        ServiceBooking saved = captor.getValue();

        assertEquals(BookingStatus.PENDING.name(), saved.getStatus());
        assertEquals(10L, saved.getServiceId());
        assertEquals(1L, saved.getCreatedBy());

        assertEquals("Cleaning", dto.serviceName());
        assertEquals("CleanCo", dto.serviceProviderName());
        assertEquals("Alice Tenant", dto.bookedByUserName());
        assertEquals(BookingStatus.PENDING.name(), dto.status());
    }

    // --- confirmBooking ---

    @Test
    void confirmBooking_throwsSP_BOOKING_NOT_FOUND_whenNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.confirmBooking(20L));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void confirmBooking_throwsSP_BOOKING_INVALID_STATUS_whenNotPending() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(20L, 10L, BookingStatus.CONFIRMED.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(b));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.confirmBooking(20L));
        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void confirmBooking_setsStatusCONFIRMED_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(20L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(b));
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(ps));
        ProviderProfile profile = makeProfile(5L, "CleanCo");
        profile.setPaymentAccountId(30L);
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));
        PaymentAccount account = new PaymentAccount();
        account.setId(30L); account.setActive(true); account.setVerified(true); account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.MPESA);
        when(accountDao.getAccountByIdAndCreatedBy(30L, 99L)).thenReturn(account);
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Alice Tenant", "alice@test.com")));
        doAnswer(inv -> { PMSInvoice invoice = inv.getArgument(0); invoice.setRef("INV-1"); return null; })
                .when(invoiceDao).createInvoice(any(PMSInvoice.class));
        doNothing().when(bookingDao).save(any(ServiceBooking.class), anyString());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Booking %s confirmed at %s. %s");

        service.confirmBooking(20L);

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        assertEquals(BookingStatus.AWAITING_PAYMENT.name(), captor.getValue().getStatus());
        assertEquals("INV-1", captor.getValue().getInvoiceRef());
    }

    // --- completeBooking ---

    @Test
    void completeBooking_throwsSP_BOOKING_INVALID_STATUS_whenNotConfirmed() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(21L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(21L, 1L)).thenReturn(Optional.of(b));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.completeBooking(21L, "proof.jpg"));
        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void completeBooking_setsStatusCOMPLETED_andCompletedAtNonNull_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(21L, 10L, BookingStatus.CONFIRMED.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(21L, 1L)).thenReturn(Optional.of(b));
        doNothing().when(bookingDao).save(any(ServiceBooking.class), anyString());
        when(userDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Booking %s completed at %s. %s");

        service.completeBooking(21L, "proof.jpg");

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        ServiceBooking saved = captor.getValue();
        assertEquals(BookingStatus.COMPLETED.name(), saved.getStatus());
        assertNotNull(saved.getCompletedAt());
        assertEquals("proof.jpg", saved.getCompletionEvidenceReference());
    }

    // --- cancelBooking ---

    @Test
    void cancelBooking_throwsSP_BOOKING_INVALID_STATUS_whenCompleted() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(22L, 10L, BookingStatus.COMPLETED.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 1L)).thenReturn(Optional.of(b));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(22L, "Changed mind"));
        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void cancelBooking_throwsSP_BOOKING_INVALID_STATUS_whenAlreadyCancelled() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(22L, 10L, BookingStatus.CANCELLED.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 1L)).thenReturn(Optional.of(b));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(22L, "Again?"));
        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
    }

    @Test
    void cancelBooking_setsStatusCANCELLED_withReason_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(22L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 1L)).thenReturn(Optional.of(b));
        doNothing().when(bookingDao).save(any(ServiceBooking.class), anyString());
        when(userDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Booking %s cancelled. %s %s");

        service.cancelBooking(22L, "No longer needed");

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        ServiceBooking saved = captor.getValue();
        assertEquals(BookingStatus.CANCELLED.name(), saved.getStatus());
        assertEquals("No longer needed", saved.getCancellationReason());
    }

    @Test
    void cancelBooking_hidesBookingFromUnrelatedUser() {
        when(userDao.getUserId()).thenReturn(77L);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 77L)).thenReturn(Optional.empty());
        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.cancelBooking(22L, "malicious"));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void paidInvoiceUnlocksBookingForProviderStart() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("UNPAID");
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));

        service.completePaidInvoice("INV-40", "PSK-40");

        assertEquals(BookingStatus.PAID.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("PSK-40", booking.getProviderReference());
        verify(bookingDao).save(booking, "SYSTEM_SP_BOOKING_PAYMENT_CONFIRMED");
    }

    @Test
    void latePaymentOnCancelledBookingRequestsRefundWithoutReopeningJob() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.CANCELLED.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("UNPAID");
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));

        service.completePaidInvoice("INV-40", "PSK-LATE");

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("REQUESTED", booking.getRefundStatus());
        verify(bookingDao).save(booking, "SYSTEM_SP_LATE_PAYMENT_REFUND_REQUESTED");
    }

    @Test
    void providerCanStartOnlyPaidBooking() {
        when(userDao.getUserId()).thenReturn(99L);
        ServiceBooking booking = makeBooking(41L, 10L, BookingStatus.PAID.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(41L, 99L)).thenReturn(Optional.of(booking));

        service.startBooking(41L);

        assertEquals(BookingStatus.IN_PROGRESS.name(), booking.getStatus());
        assertNotNull(booking.getStartedAt());
    }

    @Test
    void financeCannotSettleMoreThanCompletedBookingNetAmount() {
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        ServiceBooking booking = makeBooking(42L, 10L, BookingStatus.COMPLETED.name());
        booking.setRefundedAmount(new BigDecimal("100.00"));
        when(bookingDao.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        var request = new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                MarketplaceFinanceRequest.FinanceStatus.CONFIRMED, new BigDecimal("901.00"), "SET-42");

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.updateFinance(42L, request));

        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
    }
}
