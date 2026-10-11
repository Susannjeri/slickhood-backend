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
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ComplaintCaseDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.dao.ServiceRatingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.CreateBookingRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceBookingDTO;
import org.pms.silverocean.service.sp.wrappers.MarketplaceFinanceRequest;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
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
    @Mock private org.pms.silverocean.service.notification.BusinessNotificationService businessAlerts;
    @Mock
    private I18NService i18NService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private PaymentPlatformFactory paymentPlatformFactory;
    @Mock
    private InvoiceDao invoiceDao;
    @Mock
    private UnitDao unitDao;
    @Mock
    private ServiceRatingDao ratingDao;
    @Mock
    private ComplaintCaseDao complaintDao;
    @Mock
    private org.pms.silverocean.service.subscription.SubscriptionEntitlementService subscriptionEntitlements;
    @Mock
    private ServiceFinanceOperationService financeOperations;

    private ServiceBookingService service;
    @Test void financeAlertsKeepSettlementRecordsProviderOnly(){
        ServiceBooking booking=new ServiceBooking();booking.setId(9L);booking.setServiceId(2L);booking.setCreatedBy(8L);booking.setRefundStatus("CONFIRMED");booking.setRefundedAmount(new BigDecimal("20"));booking.setSettlementStatus("CONFIRMED");booking.setSettledAmount(new BigDecimal("80"));
        ProviderService listing=new ProviderService();listing.setProfileId(3L);ProviderProfile profile=new ProviderProfile();profile.setUserId(7L);profile.setActive(true);
        when(serviceDao.findById(2L)).thenReturn(Optional.of(listing));when(profileDao.findById(3L)).thenReturn(Optional.of(profile));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"notifyFinanceRecord",booking,true);
        verify(businessAlerts).publish(8L,"service-finance:9:refund:CONFIRMED:20","SERVICE_BOOKING_STATUS","The refund record for service booking #9 was updated.","/dashboard/marketplace?tab=bookings&bookingId=9");
        verify(businessAlerts).publish(7L,"service-finance:9:refund:CONFIRMED:20","SERVICE_BOOKING_STATUS","The refund record for service booking #9 was updated.","/dashboard/marketplace?tab=bookings&bookingId=9");
        org.mockito.Mockito.clearInvocations(businessAlerts);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"notifyFinanceRecord",booking,false);
        verify(businessAlerts).publish(7L,"service-finance:9:settlement:CONFIRMED:80","SERVICE_BOOKING_STATUS","The receiving-payment record for service booking #9 was updated.","/dashboard/marketplace?tab=bookings&bookingId=9");org.mockito.Mockito.verifyNoMoreInteractions(businessAlerts);
        org.mockito.Mockito.verifyNoInteractions(bookingDao,accountDao,invoiceDao,notificationService);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceBookingService(bookingDao, serviceDao, profileDao, userDao,
                notificationService, businessAlerts, i18NService, accountDao, paymentPlatformFactory, invoiceDao, unitDao, ratingDao,
                complaintDao, subscriptionEntitlements, financeOperations);
        when(paymentPlatformFactory.isAvailableForNewPayments(any())).thenReturn(true);
        Users providerOwner = makeUser(99L, "CleanCo owner", "owner@clean.test");
        providerOwner.setActive(true);
        providerOwner.setVerified(true);
        providerOwner.setAccountStatus(org.pms.silverocean.service.kyc.AccountStatus.ACTIVE.name());
        when(userDao.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(providerOwner));
        PaymentAccount merchantAccount = new PaymentAccount();
        merchantAccount.setId(50L);
        merchantAccount.setCreatedBy(99L);
        merchantAccount.setActive(true);
        merchantAccount.setVerified(true);
        merchantAccount.setCategory(AccountCategory.MERCHANT);
        merchantAccount.setChannel(PaymentChannel.MPESA);
        when(accountDao.findAnyAccountForUpdate(50L)).thenReturn(Optional.of(merchantAccount));
        when(serviceDao.findAnyByIdForUpdate(10L)).thenReturn(Optional.of(
                makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning")));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        when(serviceDao.findById(10L)).thenReturn(Optional.of(
                makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning")));
        when(profileDao.findById(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
    }

    private ProviderService makeProviderService(long id, long profileId, String status, String categoryName) {
        ProviderService s = new ProviderService();
        s.setId(id);
        s.setProfileId(profileId);
        s.setStatus(status);
        s.setActive(true);
        s.setCategoryName(categoryName);
        s.setPricingUnit(org.pms.silverocean.service.sp.enums.PricingUnit.PER_JOB);
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
        b.setPaymentAccountId(50L);
        b.setPaymentChannel(PaymentChannel.MPESA.name());
        b.setScheduledAt(LocalDateTime.now().plusDays(1).atZone(ZoneId.of("UTC")));
        return b;
    }

    private ProviderProfile makeProfile(long id, String businessName) {
        ProviderProfile p = new ProviderProfile();
        p.setId(id);
        p.setBusinessName(businessName);
        p.setUserId(99L);
        p.setActive(true);
        p.setStatus("ACTIVE");
        p.setPaymentAccountId(50L);
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
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), "notes", null, null);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.createBooking(request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createBooking_throwsSP_SERVICE_NOT_FOUND_whenServiceNotListed() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.SUBMITTED.name(), "Cleaning");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(ps));

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), "notes", null, null);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.createBooking(request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createBooking_savesBookingWithPendingStatus_andReturnsPopulatedDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        ps.setTitle("Deep home clean");
        ps.setDescription("A thorough two-bedroom home clean.");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(ps));
        doAnswer(inv -> { ((ServiceBooking) inv.getArgument(0)).setId(1L); return null; })
                .when(bookingDao).save(any(ServiceBooking.class), anyString());

        ProviderProfile profile = makeProfile(5L, "CleanCo");
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));

        Users booker = makeUser(1L, "Alice Tenant", "alice@test.com");
        when(userDao.findById(1L)).thenReturn(Optional.of(booker));
        when(userDao.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(booker));

        CreateBookingRequest request = new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1),
                "Please call first", null, null, "12 Market Road, Nairobi", "checkout-1");

        ServiceBookingDTO dto = service.createBooking(request);

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        ServiceBooking saved = captor.getValue();

        assertEquals(BookingStatus.PENDING.name(), saved.getStatus());
        assertEquals(10L, saved.getServiceId());
        assertEquals(1L, saved.getCreatedBy());
        assertEquals("checkout-1", saved.getClientRequestId());
        assertEquals("12 Market Road, Nairobi", saved.getServiceAddress());
        assertEquals("Deep home clean", saved.getServiceTitleSnapshot());
        assertEquals("Cleaning", saved.getServiceCategorySnapshot());
        assertEquals("A thorough two-bedroom home clean.", saved.getServiceDescriptionSnapshot());
        assertEquals("CleanCo", saved.getProviderBusinessNameSnapshot());

        assertEquals("Deep home clean", dto.serviceName());
        assertEquals("CleanCo", dto.serviceProviderName());
        assertEquals("Alice Tenant", dto.bookedByUserName());
        assertEquals(BookingStatus.PENDING.name(), dto.status());
        verify(subscriptionEntitlements).requireActiveProductForOwner(99L,
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES);
    }

    @Test
    void createBooking_rejectsProviderWhoseServicesSubscriptionExpired() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        ProviderProfile profile = makeProfile(5L, "CleanCo");
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        org.mockito.Mockito.doThrow(new PMSCustomException(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED))
                .when(subscriptionEntitlements).requireActiveProductForOwner(99L,
                        org.pms.silverocean.service.subscription.enums.SubscriptionProduct.SERVICES);

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.createBooking(
                new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), null, null, null,
                        "12 Market Road, Nairobi", null)));

        assertEquals(ResponseCode.SUBSCRIPTION_ACCESS_REQUIRED, error.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void createBooking_replaysExistingIdempotentRequestWithoutSavingDuplicate() {
        when(userDao.getUserId()).thenReturn(1L);
        Users customer = makeUser(1L, "Alice Tenant", "alice@test.com");
        when(userDao.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(customer));
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1).withNano(0);
        ServiceBooking existing = makeBooking(31L, 10L, BookingStatus.PENDING.name());
        existing.setScheduledAt(scheduledAt.atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC")));
        existing.setNotes("Please call first");
        existing.setServiceAddress("12 Market Road, Nairobi");
        existing.setClientRequestId("checkout-1");
        when(bookingDao.findByCreatedByAndClientRequestId(1L, "checkout-1")).thenReturn(Optional.of(existing));
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        listing.setTitle("Deep home clean");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        when(userDao.findById(1L)).thenReturn(Optional.of(customer));

        ServiceBookingDTO result = service.createBooking(new CreateBookingRequest(10L, scheduledAt,
                "Please call first", null, null, "12 Market Road, Nairobi", " checkout-1 "));

        assertEquals(31L, result.id());
        assertEquals("Deep home clean", result.serviceName());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void createBooking_rejectsReusedIdempotencyKeyWhenAddressChanged() {
        when(userDao.getUserId()).thenReturn(1L);
        when(userDao.findActiveByIdForUpdate(1L)).thenReturn(Optional.of(makeUser(1L, "Alice", "alice@test.com")));
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1).withNano(0);
        ServiceBooking existing = makeBooking(31L, 10L, BookingStatus.PENDING.name());
        existing.setScheduledAt(scheduledAt.atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC")));
        existing.setServiceAddress("12 Market Road, Nairobi");
        existing.setClientRequestId("checkout-1");
        when(bookingDao.findByCreatedByAndClientRequestId(1L, "checkout-1")).thenReturn(Optional.of(existing));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.createBooking(
                new CreateBookingRequest(10L, scheduledAt, null, null, null,
                        "Different address", "checkout-1")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void createBooking_requiresEitherTypedAddressOrOwnedLinkedUnit() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.createBooking(
                new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), null, null, null)));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void createBooking_acceptsOwnedLinkedUnitAndPinsItsProperty() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        DbUnitDTO unit = org.mockito.Mockito.mock(DbUnitDTO.class);
        when(unit.propertyId()).thenReturn(77L);
        when(unitDao.findByIdAndStaffOrOwnerOrTenant(9L, 1L)).thenReturn(Optional.of(unit));
        doAnswer(inv -> { ((ServiceBooking) inv.getArgument(0)).setId(32L); return null; })
                .when(bookingDao).save(any(ServiceBooking.class), anyString());

        service.createBooking(new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1),
                null, 77L, 9L, null, null));

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        assertEquals(9L, captor.getValue().getUnitId());
        assertEquals(77L, captor.getValue().getPropertyId());
        assertEquals("Linked unit 9", captor.getValue().getServiceAddress());
    }

    @Test
    void createBooking_rejectsProviderWhoseKycExpiredAfterDirectoryWasLoaded() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        Users expiredOwner = makeUser(99L, "CleanCo owner", "owner@clean.test");
        expiredOwner.setActive(true);
        expiredOwner.setVerified(false);
        expiredOwner.setAccountStatus(org.pms.silverocean.service.kyc.AccountStatus.PENDING_KYC.name());
        when(userDao.findActiveByIdForUpdate(99L)).thenReturn(Optional.of(expiredOwner));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.createBooking(
                new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), null, null, null,
                        "12 Market Road, Nairobi", null)));

        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, error.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
        verify(accountDao, never()).findAnyAccountForUpdate(anyLong());
    }

    @Test
    void createBooking_rejectsProviderWhosePaymentRailWasDisabledAfterDirectoryLoaded() {
        when(userDao.getUserId()).thenReturn(1L);
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(
                makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning")));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        when(paymentPlatformFactory.isAvailableForNewPayments(PaymentChannel.MPESA)).thenReturn(false);

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.createBooking(
                new CreateBookingRequest(10L, LocalDateTime.now().plusDays(1), null, null, null,
                        "12 Market Road, Nairobi", null)));

        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, error.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
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
    void confirmBookingExpiresARequestWhoseScheduledTimeHasAlreadyPassedWithoutCreatingAnInvoice() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(20L, 10L, BookingStatus.PENDING.name());
        booking.setScheduledAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(booking));
        when(serviceDao.findById(10L)).thenReturn(Optional.empty());

        service.confirmBooking(20L);

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("The requested service time passed before the provider confirmed it.",
                booking.getCancellationReason());
        verify(bookingDao).save(booking, "SYSTEM_SP_BOOKING_REQUEST_EXPIRED");
        verify(invoiceDao, never()).createInvoice(any());
        verify(accountDao, never()).getAccountByIdAndCreatedBy(anyLong(), anyLong());
    }

    @Test
    void confirmBooking_keepsExistingRequestPayableWhenListingIsVoluntarilyHidden() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(20L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(b));
        ProviderService ps = makeProviderService(10L, 5L, ProviderServiceStatus.HIDDEN.name(), "Cleaning");
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
        ArgumentCaptor<PMSInvoice> invoiceCaptor = ArgumentCaptor.forClass(PMSInvoice.class);
        verify(invoiceDao).createInvoice(invoiceCaptor.capture());
        assertEquals(30L, invoiceCaptor.getValue().getPaymentAccountId());
        assertEquals(99L, invoiceCaptor.getValue().getPayToUserId());
        assertEquals("SERVICE_MARKETPLACE", invoiceCaptor.getValue().getBillingType());
    }

    @Test
    void confirmBooking_rejectsRetiredFlutterWaveReceivingAccountBeforeCreatingInvoice() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(20L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(booking));
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        ProviderProfile profile = makeProfile(5L, "CleanCo");
        profile.setPaymentAccountId(30L);
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));
        PaymentAccount account = new PaymentAccount();
        account.setId(30L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.FLUTTER_WAVE);
        when(accountDao.getAccountByIdAndCreatedBy(30L, 99L)).thenReturn(account);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.confirmBooking(20L));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        assertEquals(BookingStatus.PENDING.name(), booking.getStatus());
        verify(invoiceDao, never()).createInvoice(any());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void confirmBooking_rejectsGloballyDisabledPaymentRailBeforeCreatingInvoice() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(20L, 10L, BookingStatus.PENDING.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L, 1L)).thenReturn(Optional.of(booking));
        when(serviceDao.findById(10L)).thenReturn(Optional.of(
                makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning")));
        ProviderProfile profile = makeProfile(5L, "CleanCo");
        profile.setPaymentAccountId(30L);
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));
        PaymentAccount account = new PaymentAccount();
        account.setId(30L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.PESA_LINK);
        when(accountDao.getAccountByIdAndCreatedBy(30L, 99L)).thenReturn(account);
        when(paymentPlatformFactory.isAvailableForNewPayments(PaymentChannel.PESA_LINK)).thenReturn(false);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.confirmBooking(20L));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        assertEquals(BookingStatus.PENDING.name(), booking.getStatus());
        verify(invoiceDao, never()).createInvoice(any());
        verify(bookingDao, never()).save(any(), anyString());
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
    void completeBooking_startsFortyEightHourCustomerConfirmationWindow() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking b = makeBooking(21L, 10L, BookingStatus.IN_PROGRESS.name());
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(21L, 1L)).thenReturn(Optional.of(b));
        doNothing().when(bookingDao).save(any(ServiceBooking.class), anyString());
        when(userDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Booking %s completed at %s. %s");

        service.completeBooking(21L, "proof.jpg");

        ArgumentCaptor<ServiceBooking> captor = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(bookingDao).save(captor.capture(), anyString());
        ServiceBooking saved = captor.getValue();
        assertEquals(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), saved.getStatus());
        assertNotNull(saved.getCompletedAt());
        assertEquals(java.time.Duration.ofHours(48),
                java.time.Duration.between(saved.getCompletedAt(), saved.getDisputeDeadlineAt()));
        assertEquals("proof.jpg", saved.getCompletionEvidenceReference());
    }

    @Test
    void acceptCompletion_customerMovesAwaitingBookingToCompleted() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        java.time.ZonedDateTime providerCompletedAt = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(1);
        booking.setCompletedAt(providerCompletedAt);
        booking.setDisputeDeadlineAt(providerCompletedAt.plusHours(48));
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("NOT_REQUIRED");
        booking.setSettlementStatus("PENDING");
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 1L)).thenReturn(Optional.of(booking));

        service.acceptCompletion(21L);

        assertEquals(BookingStatus.COMPLETED.name(), booking.getStatus());
        assertEquals(providerCompletedAt, booking.getCompletedAt());
        org.junit.jupiter.api.Assertions.assertFalse(booking.getDisputeDeadlineAt()
                .isAfter(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC"))));
        assertEquals("REQUESTED", booking.getSettlementStatus());
        assertEquals(0, booking.getQuotedAmount().compareTo(booking.getSettledAmount()));
        org.junit.jupiter.api.Assertions.assertNull(booking.getSettlementReference());
        verify(bookingDao).save(booking, "CUSTOMER_CONFIRMED_SP_BOOKING_COMPLETION");
        verify(businessAlerts).publish(1L, "service-booking:21:COMPLETED:PAID:NOT_REQUIRED",
                "SERVICE_BOOKING_STATUS", "Service booking 21: completed.",
                "/dashboard/marketplace?tab=bookings&bookingId=21");
    }

    @Test
    void acceptCompletion_holdsSettlementWhenRefundIsInProgress() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        booking.setCompletedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(1));
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusHours(47));
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("PROCESSING");
        booking.setSettlementStatus("PENDING");
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 1L)).thenReturn(Optional.of(booking));

        service.acceptCompletion(21L);

        assertEquals(BookingStatus.COMPLETED.name(), booking.getStatus());
        assertEquals("PENDING", booking.getSettlementStatus());
        org.junit.jupiter.api.Assertions.assertNull(booking.getSettledAmount());
        verify(bookingDao).save(booking, "CUSTOMER_CONFIRMED_SP_BOOKING_COMPLETION");
    }

    @Test
    void acceptCompletion_holdsSettlementWhenRefundAttemptFailedAndNeedsRetry() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        booking.setCompletedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(1));
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusHours(47));
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("FAILED");
        booking.setSettlementStatus("PENDING");
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 1L)).thenReturn(Optional.of(booking));

        service.acceptCompletion(21L);

        assertEquals(BookingStatus.COMPLETED.name(), booking.getStatus());
        assertEquals("PENDING", booking.getSettlementStatus());
        org.junit.jupiter.api.Assertions.assertNull(booking.getSettledAmount());
    }

    @Test
    void acceptCompletion_providerCannotAcceptOnCustomersBehalf() {
        when(userDao.getUserId()).thenReturn(99L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 99L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.acceptCompletion(21L));

        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void acceptCompletion_rejectsBookingThatIsNotAwaitingCustomer() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.IN_PROGRESS.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.acceptCompletion(21L));

        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void acceptCompletion_rejectsWhileComplaintIsOpen() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(21L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(21L, 1L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsOpenComplaintForBooking(21L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.acceptCompletion(21L));

        assertEquals(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void autoFinalization_completesExpiredUndisputedBookingAndReleasesSettlement() {
        ServiceBooking booking = makeBooking(31L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        java.time.ZonedDateTime providerCompletedAt = java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(49);
        java.time.ZonedDateTime deadline = providerCompletedAt.plusHours(48);
        booking.setCompletedAt(providerCompletedAt);
        booking.setDisputeDeadlineAt(deadline);
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("NOT_REQUIRED");
        booking.setSettlementStatus("PENDING");
        when(bookingDao.findIdsReadyForAutoFinalization(
                any(java.time.ZonedDateTime.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(31L));
        when(bookingDao.findByIdForUpdate(31L)).thenReturn(Optional.of(booking));

        service.autoFinalizeExpiredCustomerConfirmations();

        assertEquals(BookingStatus.COMPLETED.name(), booking.getStatus());
        assertEquals(providerCompletedAt, booking.getCompletedAt());
        assertEquals(deadline, booking.getDisputeDeadlineAt());
        assertEquals("REQUESTED", booking.getSettlementStatus());
        assertEquals(0, booking.getQuotedAmount().compareTo(booking.getSettledAmount()));
        verify(complaintDao).existsOpenComplaintForBooking(31L);
        verify(bookingDao).save(booking, "SYSTEM_AUTO_FINALIZED_SP_BOOKING_COMPLETION");
    }

    @Test
    void autoFinalization_doesNotFinalizeWhenComplaintAppearsAfterCandidateScan() {
        ServiceBooking booking = makeBooking(32L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        booking.setCompletedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(49));
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusHours(1));
        booking.setPaymentStatus("PAID");
        booking.setSettlementStatus("PENDING");
        when(bookingDao.findIdsReadyForAutoFinalization(
                any(java.time.ZonedDateTime.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(32L));
        when(bookingDao.findByIdForUpdate(32L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsOpenComplaintForBooking(32L)).thenReturn(true);

        service.autoFinalizeExpiredCustomerConfirmations();

        assertEquals(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), booking.getStatus());
        assertEquals("PENDING", booking.getSettlementStatus());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void autoFinalization_rechecksDeadlineAfterTakingBookingLock() {
        ServiceBooking booking = makeBooking(33L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        booking.setCompletedAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")));
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusHours(48));
        when(bookingDao.findIdsReadyForAutoFinalization(
                any(java.time.ZonedDateTime.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(33L));
        when(bookingDao.findByIdForUpdate(33L)).thenReturn(Optional.of(booking));

        service.autoFinalizeExpiredCustomerConfirmations();

        assertEquals(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(), booking.getStatus());
        verify(complaintDao, never()).existsOpenComplaintForBooking(33L);
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void staleRequestExpiryVoidsOutstandingInvoiceAndQueuesAnyCollectedBalanceForRefund() {
        ServiceBooking booking = makeBooking(34L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setScheduledAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        booking.setInvoiceRef("INV-34");
        booking.setPaymentStatus("UNPAID");
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(34L);
        invoice.setRef("INV-34");
        invoice.setCurrency("KES");
        invoice.setMoneyAmount(new BigDecimal("1000.00"));
        invoice.setMoneyPendingAmount(new BigDecimal("750.00"));
        invoice.setActive(true);
        when(bookingDao.findIdsReadyForRequestExpiry(any(java.time.ZonedDateTime.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(34L));
        when(bookingDao.findByIdForUpdate(34L)).thenReturn(Optional.of(booking));
        when(invoiceDao.getInvoiceByRefForUpdate("INV-34")).thenReturn(Optional.of(invoice));

        service.expireStaleUnpaidRequests();

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("PARTIALLY_PAID", booking.getPaymentStatus());
        assertEquals("REQUESTED", booking.getRefundStatus());
        assertEquals(new BigDecimal("250.00"), booking.getRefundedAmount());
        assertEquals(false, invoice.isActive());
        verify(invoiceDao).saveInvoice(invoice);
        verify(bookingDao).save(booking, "SYSTEM_SP_BOOKING_REQUEST_EXPIRED");
    }

    @Test
    void staleRequestExpiryNeverCancelsAPaidBookingEvenIfItWasInTheCandidateSnapshot() {
        ServiceBooking booking = makeBooking(35L, 10L, BookingStatus.PAID.name());
        booking.setScheduledAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        booking.setPaymentStatus("PAID");
        when(bookingDao.findIdsReadyForRequestExpiry(any(java.time.ZonedDateTime.class),
                any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(35L));
        when(bookingDao.findByIdForUpdate(35L)).thenReturn(Optional.of(booking));

        service.expireStaleUnpaidRequests();

        assertEquals(BookingStatus.PAID.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        verify(invoiceDao, never()).getInvoiceByRefForUpdate(anyString());
        verify(bookingDao, never()).save(any(), anyString());
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
    void cancelBooking_throwsSP_BOOKING_INVALID_STATUS_whenAwaitingCustomerConfirmation() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(22L, 10L, BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name());
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(22L, "Changed mind"));

        assertEquals(ResponseCode.SP_BOOKING_INVALID_STATUS, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
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
    void providerCancellationVoidsPartiallyPaidInvoiceAndQueuesTheExactCollectedAmountForRefund() {
        when(userDao.getUserId()).thenReturn(99L);
        ServiceBooking booking = makeBooking(23L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-23");
        booking.setPaymentStatus("UNPAID");
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(23L);
        invoice.setRef("INV-23");
        invoice.setCurrency("KES");
        invoice.setMoneyAmount(new BigDecimal("1000.00"));
        invoice.setMoneyPendingAmount(new BigDecimal("600.00"));
        invoice.setActive(true);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(23L, 99L)).thenReturn(Optional.of(booking));
        when(invoiceDao.getInvoiceByRefForUpdate("INV-23")).thenReturn(Optional.of(invoice));
        when(userDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Booking %s cancelled. %s %s");

        service.cancelBooking(23L, "No longer needed");

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("PARTIALLY_PAID", booking.getPaymentStatus());
        assertEquals("REQUESTED", booking.getRefundStatus());
        assertEquals(new BigDecimal("400.00"), booking.getRefundedAmount());
        assertEquals(false, invoice.isActive());
        assertEquals(false, invoice.isTransactionInProgress());
        verify(invoiceDao).saveInvoice(invoice);
        verify(bookingDao).save(booking, org.pms.silverocean.service.auth.roles.enums.Permission.CANCEL_SP_BOOKING);
    }

    @Test
    void customerCannotCancelAfterAnyPaymentWasCollected() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(25L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-25");
        booking.setPaymentStatus("UNPAID");
        PMSInvoice invoice = new PMSInvoice();
        invoice.setId(25L);
        invoice.setRef("INV-25");
        invoice.setMoneyAmount(new BigDecimal("1000.00"));
        invoice.setMoneyPendingAmount(new BigDecimal("600.00"));
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(25L, 1L)).thenReturn(Optional.of(booking));
        when(invoiceDao.getInvoiceByRefForUpdate("INV-25")).thenReturn(Optional.of(invoice));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(25L, "No longer needed"));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        assertEquals(BookingStatus.AWAITING_PAYMENT.name(), booking.getStatus());
        verify(bookingDao, never()).save(any(), anyString());
        verify(invoiceDao, never()).saveInvoice(any());
    }

    @Test
    void customerCannotCancelPaidBookingBeforeProviderStarts() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(26L, 10L, BookingStatus.PAID.name());
        booking.setPaymentStatus("PAID");
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(26L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(26L, "Changed my mind"));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        assertEquals(BookingStatus.PAID.name(), booking.getStatus());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void customerCannotCancelServiceInProgress() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(27L, 10L, BookingStatus.IN_PROGRESS.name());
        booking.setPaymentStatus("PAID");
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(27L, 1L)).thenReturn(Optional.of(booking));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.cancelBooking(27L, "The work is disputed"));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        assertEquals(BookingStatus.IN_PROGRESS.name(), booking.getStatus());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void financeCanConfirmOnlyTheExactRefundForACancelledPartialPayment() {
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        when(userDao.getUserId()).thenReturn(77L);
        ServiceBooking booking = makeBooking(24L, 10L, BookingStatus.CANCELLED.name());
        booking.setPaymentStatus("PARTIALLY_PAID");
        booking.setRefundStatus("REQUESTED");
        booking.setRefundedAmount(new BigDecimal("400.00"));
        when(bookingDao.findByIdForUpdate(24L)).thenReturn(Optional.of(booking));
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        when(serviceDao.findById(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findById(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        when(userDao.findById(1L)).thenReturn(Optional.of(makeUser(1L, "Customer", "customer@test")));

        service.updateFinance(24L, new MarketplaceFinanceRequest(
                MarketplaceFinanceRequest.FinanceType.REFUND,
                MarketplaceFinanceRequest.FinanceStatus.CONFIRMED,
                new BigDecimal("400.00"), "REF-PARTIAL-24"));

        assertEquals("REFUNDED", booking.getPaymentStatus());
        assertEquals("CONFIRMED", booking.getRefundStatus());
        assertEquals(new BigDecimal("400.00"), booking.getRefundedAmount());
        assertEquals("REF-PARTIAL-24", booking.getRefundReference());
        verify(bookingDao).save(booking, "SP_BOOKING_FINANCE_REFUND_CONFIRMED");
        verify(financeOperations).recordConfirmed(booking, 99L,
                MarketplaceFinanceRequest.FinanceType.REFUND, new BigDecimal("400.00"),
                "REF-PARTIAL-24", 77L);
    }

    @Test
    void cancelBooking_hidesBookingFromUnrelatedUser() {
        when(userDao.getUserId()).thenReturn(77L);
        when(bookingDao.findByIdAndCustomerOrProviderForUpdate(22L, 77L)).thenReturn(Optional.empty());
        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.cancelBooking(22L, "malicious"));
        assertEquals(ResponseCode.SP_BOOKING_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void paidInvoiceUnlocksExistingBookingWhenListingIsVoluntarilyHidden() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("UNPAID");
        booking.setPaymentAccountId(30L);
        booking.setPaymentChannel(PaymentChannel.MPESA.name());
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.HIDDEN.name(), "Cleaning");
        when(serviceDao.findAnyByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        PaymentAccount account = new PaymentAccount();
        account.setId(30L);
        account.setCreatedBy(99L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.MPESA);
        when(accountDao.findAnyAccountForUpdate(30L)).thenReturn(Optional.of(account));

        service.completePaidInvoice("INV-40", "PSK-40");

        assertEquals(BookingStatus.PAID.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("PSK-40", booking.getProviderReference());
        verify(bookingDao).save(booking, "SYSTEM_SP_BOOKING_PAYMENT_CONFIRMED");
    }

    @Test
    void paymentCapturedAfterServiceSuspensionRequestsRefundWithoutUnlockingWork() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("UNPAID");
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.SUSPENDED.name(), "Cleaning");
        when(serviceDao.findAnyByIdForUpdate(10L)).thenReturn(Optional.of(listing));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));

        service.completePaidInvoice("INV-40", "PSK-40");

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("REQUESTED", booking.getRefundStatus());
        assertEquals("PSK-40", booking.getProviderReference());
        verify(bookingDao).save(booking, "SYSTEM_SP_ENFORCEMENT_PAYMENT_REFUND_REQUESTED");
    }

    @Test
    void paymentCapturedAfterReceivingAccountRevocationRequestsRefund() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.AWAITING_PAYMENT.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("UNPAID");
        booking.setPaymentAccountId(30L);
        booking.setPaymentChannel(PaymentChannel.MPESA.name());
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));
        when(serviceDao.findAnyByIdForUpdate(10L)).thenReturn(Optional.of(
                makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning")));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L, "CleanCo")));
        PaymentAccount revoked = new PaymentAccount();
        revoked.setId(30L);
        revoked.setCreatedBy(99L);
        revoked.setActive(false);
        revoked.setVerified(true);
        revoked.setCategory(AccountCategory.MERCHANT);
        revoked.setChannel(PaymentChannel.MPESA);
        when(accountDao.findAnyAccountForUpdate(30L)).thenReturn(Optional.of(revoked));

        service.completePaidInvoice("INV-40", "PSK-40");

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("REQUESTED", booking.getRefundStatus());
        verify(bookingDao).save(booking, "SYSTEM_SP_ENFORCEMENT_PAYMENT_REFUND_REQUESTED");
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
    void paidInvoiceReplayDoesNotReopenAnAlreadyRefundedBooking() {
        ServiceBooking booking = makeBooking(40L, 10L, BookingStatus.CANCELLED.name());
        booking.setInvoiceRef("INV-40");
        booking.setPaymentStatus("REFUNDED");
        booking.setProviderReference("PSK-ORIGINAL");
        booking.setRefundStatus("CONFIRMED");
        booking.setRefundReference("REFUND-40");
        booking.setRefundedAmount(booking.getQuotedAmount());
        when(bookingDao.findByInvoiceRefForUpdate("INV-40")).thenReturn(Optional.of(booking));

        service.completePaidInvoice("INV-40", "PSK-REPLAY");

        assertEquals(BookingStatus.CANCELLED.name(), booking.getStatus());
        assertEquals("REFUNDED", booking.getPaymentStatus());
        assertEquals("PSK-ORIGINAL", booking.getProviderReference());
        assertEquals("CONFIRMED", booking.getRefundStatus());
        assertEquals("REFUND-40", booking.getRefundReference());
        verify(bookingDao, never()).save(any(), anyString());
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
        booking.setPaymentStatus("PAID");
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        booking.setRefundStatus("CONFIRMED");
        booking.setRefundedAmount(new BigDecimal("100.00"));
        when(bookingDao.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        var request = new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                MarketplaceFinanceRequest.FinanceStatus.CONFIRMED, new BigDecimal("901.00"), "SET-42");

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.updateFinance(42L, request));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
    }

    @Test
    void financeCannotConfirmLessThanTheRequestedSettlement() {
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        ServiceBooking booking = makeBooking(44L, 10L, BookingStatus.COMPLETED.name());
        booking.setPaymentStatus("PAID");
        booking.setRefundStatus("NOT_REQUIRED");
        booking.setSettlementStatus("REQUESTED");
        booking.setSettledAmount(new BigDecimal("1000.00"));
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).minusMinutes(1));
        when(bookingDao.findByIdForUpdate(44L)).thenReturn(Optional.of(booking));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.updateFinance(44L,
                new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                        MarketplaceFinanceRequest.FinanceStatus.CONFIRMED,
                        new BigDecimal("900.00"), "SET-44")));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        assertEquals("REQUESTED", booking.getSettlementStatus());
        assertEquals(new BigDecimal("1000.00"), booking.getSettledAmount());
        verify(financeOperations, never()).recordConfirmed(any(), anyLong(), any(), any(), anyString(), anyLong());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void financeCannotSettleBookingWhileComplaintIsOpen() {
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        ServiceBooking booking = makeBooking(42L, 10L, BookingStatus.COMPLETED.name());
        booking.setPaymentStatus("PAID");
        when(bookingDao.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(complaintDao.existsOpenComplaintForBooking(42L)).thenReturn(true);
        var request = new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                MarketplaceFinanceRequest.FinanceStatus.CONFIRMED, new BigDecimal("100.00"), "SET-42");

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.updateFinance(42L, request));

        assertEquals(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test
    void financeCannotSettleBeforeCustomerDisputeWindowEnds() {
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        ServiceBooking booking = makeBooking(43L, 10L, BookingStatus.COMPLETED.name());
        booking.setPaymentStatus("PAID");
        booking.setDisputeDeadlineAt(java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")).plusHours(1));
        when(bookingDao.findByIdForUpdate(43L)).thenReturn(Optional.of(booking));
        var request = new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.SETTLEMENT,
                MarketplaceFinanceRequest.FinanceStatus.CONFIRMED, new BigDecimal("100.00"), "SET-43");

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.updateFinance(43L, request));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        verify(bookingDao, never()).save(any(), anyString());
    }

    @Test void fullAuthorisedRefundPreventsUncompletedJobFromStartingAndRetryIsIdempotent(){
        when(userDao.hasRole(PMSRole.FINANCE)).thenReturn(true);
        var booking=makeBooking(42L,10L,BookingStatus.PAID.name());booking.setPaymentStatus("PAID");
        when(bookingDao.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        var request=new MarketplaceFinanceRequest(MarketplaceFinanceRequest.FinanceType.REFUND,MarketplaceFinanceRequest.FinanceStatus.CONFIRMED,booking.getQuotedAmount(),"REFUND-42");
        service.updateFinance(42L,request);assertEquals("REFUNDED",booking.getPaymentStatus());assertEquals("CANCELLED",booking.getStatus());
        service.updateFinance(42L,request);
        verify(bookingDao,org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.eq(booking),anyString());
    }

    @Test void bookingRejectsBlacklistedProviderEvenIfServiceRemainsListed(){
        when(userDao.getUserId()).thenReturn(1L);
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(makeProviderService(10L,5L,"LISTED","Cleaning")));
        ProviderProfile profile=makeProfile(5L,"CleanCo");profile.setStatus("BLACKLISTED");
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        assertThrows(PMSCustomException.class,()->service.createBooking(new CreateBookingRequest(10L,LocalDateTime.now().plusDays(1),null,null,null)));
        org.mockito.Mockito.verifyNoInteractions(bookingDao);
    }
    @Test void bookingRejectsPastNairobiTimeAtServiceBoundary(){
        when(userDao.getUserId()).thenReturn(1L);
        when(serviceDao.findByIdForUpdate(10L)).thenReturn(Optional.of(makeProviderService(10L,5L,"LISTED","Cleaning")));
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(makeProfile(5L,"CleanCo")));
        assertThrows(PMSCustomException.class,()->service.createBooking(new CreateBookingRequest(10L,LocalDateTime.now(ZoneId.of("Africa/Nairobi")).minusMinutes(1),null,null,null)));
        org.mockito.Mockito.verifyNoInteractions(bookingDao);
    }
    @Test void inactivePaymentAccountCannotIssueInvoice(){
        when(userDao.getUserId()).thenReturn(99L);
        ServiceBooking booking=makeBooking(20L,10L,"PENDING");
        when(bookingDao.findByIdAndServiceCreatedByForUpdate(20L,99L)).thenReturn(Optional.of(booking));
        when(serviceDao.findById(10L)).thenReturn(Optional.of(makeProviderService(10L,5L,"LISTED","Cleaning")));
        ProviderProfile profile=makeProfile(5L,"CleanCo");profile.setPaymentAccountId(30L);
        when(profileDao.findById(5L)).thenReturn(Optional.of(profile));
        PaymentAccount account=new PaymentAccount();account.setActive(false);account.setVerified(true);account.setCategory(AccountCategory.MERCHANT);
        when(accountDao.getAccountByIdAndCreatedBy(30L,99L)).thenReturn(account);
        assertThrows(PMSCustomException.class,()->service.confirmBooking(20L));
        org.mockito.Mockito.verifyNoInteractions(invoiceDao);
    }
    @Test void bookingPerspectiveIsPerTransactionNotGlobalProviderRole(){
        ServiceBooking booking=makeBooking(20L,10L,"AWAITING_PAYMENT");
        org.junit.jupiter.api.Assertions.assertTrue(new ServiceBookingDTO(booking,"Cleaning","CleanCo","Customer",1L).customerBooking());
        org.junit.jupiter.api.Assertions.assertFalse(new ServiceBookingDTO(booking,"Cleaning","CleanCo","Customer",99L).customerBooking());
    }

    @Test
    void listMyBookings_returnsListingTitleAndWhetherCustomerAlreadyRatedIt() {
        when(userDao.getUserId()).thenReturn(1L);
        ServiceBooking booking = makeBooking(51L, 10L, BookingStatus.COMPLETED.name());
        ProviderService listing = makeProviderService(10L, 5L, ProviderServiceStatus.LISTED.name(), "Cleaning");
        listing.setTitle("Move-out cleaning");
        ProviderProfile profile = makeProfile(5L, "CleanCo");
        Users customer = makeUser(1L, "Alice Tenant", "alice@test.com");
        var requested = PageRequest.of(0, 20);
        when(bookingDao.findByCreatedByOrBookedServiceProvider(any(), anyLong()))
                .thenReturn(new PageImpl<>(List.of(booking), requested, 1));
        when(serviceDao.findAllById(List.of(10L))).thenReturn(List.of(listing));
        when(profileDao.findAllById(List.of(5L))).thenReturn(List.of(profile));
        when(userDao.findAllById(List.of(1L))).thenReturn(List.of(customer));
        when(ratingDao.existsByBookingIdAndRater(51L, 1L)).thenReturn(true);

        ServiceBookingDTO result = service.listMyBookings(requested).getContent().get(0);

        assertEquals("Move-out cleaning", result.serviceName());
        assertEquals("CleanCo", result.serviceProviderName());
        assertEquals("Alice Tenant", result.bookedByUserName());
        org.junit.jupiter.api.Assertions.assertTrue(result.customerBooking());
        org.junit.jupiter.api.Assertions.assertTrue(result.rated());
    }
}
