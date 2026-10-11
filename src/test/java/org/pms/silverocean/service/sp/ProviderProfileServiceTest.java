package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.enums.ProviderProfileStatus;
import org.pms.silverocean.service.sp.wrappers.ProviderProfileDTO;
import org.pms.silverocean.service.sp.wrappers.PaymentAccountRequest;
import org.pms.silverocean.service.sp.wrappers.SetupProfileRequest;
import org.pms.silverocean.service.users.ProfileType;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderProfileServiceTest {

    @Mock
    private ProviderProfileDao profileDao;

    @Mock
    private UserDao userDao;
    @Mock
    private AccountDao accountDao;
    @Mock
    private MarketplaceKycGate marketplaceKycGate;
    @Mock
    private NotificationService notificationService;
    @Mock
    private I18NService i18NService;
    @Mock
    private PaymentPlatformFactory paymentPlatformFactory;

    private ProviderProfileService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Users activeUser = new Users();
        activeUser.setActive(true);
        when(userDao.findActiveByIdForUpdate(anyLong())).thenReturn(Optional.of(activeUser));
        when(paymentPlatformFactory.isAvailableForNewPayments(any())).thenReturn(true);
        service = new ProviderProfileService(profileDao, userDao, accountDao,
                marketplaceKycGate, notificationService, i18NService, paymentPlatformFactory);
    }

    @Test
    void setupProfile_throwsSP_PROFILE_ALREADY_EXISTS_whenProfileExists() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(true);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", true,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test void editProfilePreservesConsentAndReceivingAccountAndRequiresReviewAgain(){
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile=new ProviderProfile();profile.setId(5L);profile.setUserId(1L);profile.setActive(true);profile.setStatus("ACTIVE");profile.setPaymentAccountId(33L);
        var originalConsent=ZonedDateTime.now().minusDays(10);profile.setConsentTimestamp(originalConsent);
        profile.setReviewedByUserId(88L);profile.setReviewedAt(ZonedDateTime.now().minusDays(1));
        when(profileDao.findByUserId(1L)).thenReturn(Optional.of(profile));
        var result=service.editMyProfile(new SetupProfileRequest("Updated name",true,-1.2,36.8));
        assertEquals("Updated name",result.businessName());assertEquals(33L,result.paymentAccountId());assertEquals(originalConsent,result.consentTimestamp());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(),result.status());
        assertNull(profile.getReviewedAt());assertNull(profile.getReviewedByUserId());
        verify(profileDao,never()).existsByUserId(anyLong());
    }

    @Test void editingBlacklistedProfileCannotReactivateIt(){
        when(userDao.getUserId()).thenReturn(1L);ProviderProfile profile=new ProviderProfile();profile.setStatus("BLACKLISTED");
        when(profileDao.findByUserId(1L)).thenReturn(Optional.of(profile));
        assertThrows(PMSCustomException.class,()->service.editMyProfile(new SetupProfileRequest("Name",true,-1.2,36.8)));
        org.mockito.Mockito.verify(profileDao,org.mockito.Mockito.never()).save(any(),anyString());
    }

    @Test
    void setupProfile_throwsSP_PROFILE_CONSENT_REQUIRED_whenConsentIsNull() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(false);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", null,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_CONSENT_REQUIRED, ex.getResponseCode());
    }

    @Test
    void setupProfile_throwsSP_PROFILE_CONSENT_REQUIRED_whenConsentIsFalse() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.existsByUserId(1L)).thenReturn(false);

        SetupProfileRequest request = new SetupProfileRequest("Acme Ltd", false,  1.0, 2.0);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setupProfile(request, "127.0.0.1"));
        assertEquals(ResponseCode.SP_PROFILE_CONSENT_REQUIRED, ex.getResponseCode());
    }

    @Test
    void setupProfile_savesProfileWithCorrectFieldsAndReturnDTO_onSuccess() {
        when(userDao.getUserId()).thenReturn(42L);
        when(profileDao.existsByUserId(42L)).thenReturn(false);
        doAnswer(inv -> { ((ProviderProfile) inv.getArgument(0)).setId(1L); return null; })
                .when(profileDao).save(any(ProviderProfile.class), anyString());

        SetupProfileRequest request = new SetupProfileRequest("Best Cleaners", true, -1.28, 36.82);

        ProviderProfileDTO dto = service.setupProfile(request, "127.0.0.1");

        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDao).save(captor.capture(), anyString());
        ProviderProfile saved = captor.getValue();

        assertEquals("Best Cleaners", saved.getBusinessName());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(), saved.getStatus());
        assertTrue(saved.isActive());
        assertEquals(42L, saved.getUserId());
        assertEquals("Professional services provided by Best Cleaners.", saved.getDescription());
        assertEquals("Location shown on the map", saved.getServiceArea());
        assertEquals("Best Cleaners", dto.businessName());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(), dto.status());
        org.mockito.InOrder setupOrder = org.mockito.Mockito.inOrder(userDao, profileDao);
        setupOrder.verify(userDao).findActiveByIdForUpdate(42L);
        setupOrder.verify(profileDao).existsByUserId(42L);
    }

    @Test
    void getMyProfile_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserId(1L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.getMyProfile());
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void getMyProfile_returnsDTOOnSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = new ProviderProfile();
        profile.setId(10L);
        profile.setBusinessName("My Biz");
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
        when(profileDao.findByUserId(1L)).thenReturn(Optional.of(profile));

        ProviderProfileDTO dto = service.getMyProfile();
        assertEquals("My Biz", dto.businessName());
        assertEquals(ProviderProfileStatus.ACTIVE.name(), dto.status());
    }

    @Test
    void approveProfile_requiresKycAndVerifiedMerchantAccount_thenActivatesProfile() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setVersion(7L);
        profile.setPaymentAccountId(33L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));

        Users provider = makeUser(42L, "provider@example.com", ProfileType.INDIVIDUAL);
        when(userDao.findById(42L)).thenReturn(Optional.of(provider));
        when(userDao.getUserId()).thenReturn(900L);

        PaymentAccount account = new PaymentAccount();
        account.setId(33L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.MPESA);
        when(accountDao.getAccountByIdAndCreatedBy(33L, 42L)).thenReturn(account);
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Approved %s");

        ProviderProfileDTO result = service.approveProfile(5L, 7L, "Identity and account verified");

        assertEquals(ProviderProfileStatus.ACTIVE.name(), result.status());
        assertEquals(7L, result.version());
        assertEquals("Identity and account verified", profile.getAdminNotes());
        assertEquals(900L, profile.getReviewedByUserId());
        assertNotNull(profile.getReviewedAt());
        verify(marketplaceKycGate).require(42L, "PROVIDER_TYPE", "SERVICE_PROVIDER", ProfileType.INDIVIDUAL);
        verify(profileDao).save(profile, Permission.APPROVE_SP_SERVICE);
        verify(notificationService).queueEmailAndInApp(eq("provider@example.com"), any(), eq("Approved CleanCo"),
                eq("SERVICE_PROVIDER_PROFILE_REVIEW"), anyString());
    }

    @Test
    void approveProfile_rejectsMissingReceivingAccountBeforeActivation() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        when(userDao.findById(42L)).thenReturn(Optional.of(makeUser(42L, "provider@example.com", ProfileType.INDIVIDUAL)));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveProfile(5L, 0L, "Ready"));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        verify(marketplaceKycGate).require(42L, "PROVIDER_TYPE", "SERVICE_PROVIDER", ProfileType.INDIVIDUAL);
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void approveProfile_rejectsRetiredFlutterWaveReceivingAccount() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setVersion(3L);
        profile.setPaymentAccountId(33L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        when(userDao.findById(42L)).thenReturn(Optional.of(makeUser(42L, "provider@example.com", ProfileType.INDIVIDUAL)));
        PaymentAccount account = new PaymentAccount();
        account.setId(33L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.FLUTTER_WAVE);
        when(accountDao.getAccountByIdAndCreatedBy(33L, 42L)).thenReturn(account);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveProfile(5L, 3L, "Ready"));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void setPaymentAccount_rejectsRetiredFlutterWaveAccount() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        when(userDao.getUserId()).thenReturn(42L);
        when(profileDao.findByUserId(42L)).thenReturn(Optional.of(profile));
        PaymentAccount account = new PaymentAccount();
        account.setId(33L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.FLUTTER_WAVE);
        when(accountDao.getAccountByIdAndCreatedBy(33L, 42L)).thenReturn(account);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setPaymentAccount(new PaymentAccountRequest(33L)));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        assertNull(profile.getPaymentAccountId());
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void setPaymentAccount_rejectsGloballyDisabledPaymentRail() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        when(userDao.getUserId()).thenReturn(42L);
        when(profileDao.findByUserId(42L)).thenReturn(Optional.of(profile));
        PaymentAccount account = new PaymentAccount();
        account.setId(33L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.MPESA);
        when(accountDao.getAccountByIdAndCreatedBy(33L, 42L)).thenReturn(account);
        when(paymentPlatformFactory.isAvailableForNewPayments(PaymentChannel.MPESA)).thenReturn(false);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.setPaymentAccount(new PaymentAccountRequest(33L)));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        assertNull(profile.getPaymentAccountId());
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void approveProfile_rejectsGloballyDisabledPaymentRail() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setPaymentAccountId(33L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        when(userDao.findById(42L)).thenReturn(Optional.of(makeUser(42L, "provider@example.com", ProfileType.INDIVIDUAL)));
        PaymentAccount account = new PaymentAccount();
        account.setId(33L);
        account.setActive(true);
        account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);
        account.setChannel(PaymentChannel.PAYSTACK);
        when(accountDao.getAccountByIdAndCreatedBy(33L, 42L)).thenReturn(account);
        when(paymentPlatformFactory.isAvailableForNewPayments(PaymentChannel.PAYSTACK)).thenReturn(false);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveProfile(5L, 0L, "Ready"));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(), profile.getStatus());
        verify(profileDao, never()).save(any(), anyString());
    }

    @Test
    void approveProfile_rejectsStaleReviewedVersionBeforeRunningApprovalChecks() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setVersion(8L);
        profile.setPaymentAccountId(33L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveProfile(5L, 7L, "Looks good"));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(), profile.getStatus());
        verify(userDao, never()).findById(anyLong());
        verify(marketplaceKycGate, never()).require(anyLong(), anyString(), anyString(), any());
        verify(profileDao, never()).save(any(), anyString());
        verify(notificationService, never()).queueEmailAndInApp(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectProfile_recordsReasonReviewerAndNotifiesProvider() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setVersion(12L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        when(userDao.getUserId()).thenReturn(900L);
        when(userDao.findById(42L)).thenReturn(Optional.of(makeUser(42L, "provider@example.com", ProfileType.INDIVIDUAL)));
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Rejected %s: %s");

        ProviderProfileDTO result = service.rejectProfile(5L, 12L, "  Replace the blurred ID  ");

        assertEquals(ProviderProfileStatus.REJECTED.name(), result.status());
        assertEquals(12L, result.version());
        assertEquals("Replace the blurred ID", profile.getAdminNotes());
        assertEquals(900L, profile.getReviewedByUserId());
        assertNotNull(profile.getReviewedAt());
        verify(profileDao).save(profile, Permission.APPROVE_SP_SERVICE);
        verify(notificationService).queueEmailAndInApp(eq("provider@example.com"), any(),
                eq("Rejected CleanCo: Replace the blurred ID"), eq("SERVICE_PROVIDER_PROFILE_REVIEW"), anyString());
    }

    @Test
    void rejectProfile_rejectsStaleReviewedVersionWithoutChangingProfile() {
        ProviderProfile profile = pendingProfile(5L, 42L, "CleanCo");
        profile.setVersion(13L);
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rejectProfile(5L, 12L, "Reject old details"));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        assertEquals(ProviderProfileStatus.PENDING_VERIFICATION.name(), profile.getStatus());
        assertNull(profile.getAdminNotes());
        verify(userDao, never()).getUserId();
        verify(profileDao, never()).save(any(), anyString());
        verify(notificationService, never()).queueEmailAndInApp(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void getProfileById_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(profileDao.findById(99L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.getProfileById(99L));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void blacklistProfile_throwsSP_PROFILE_NOT_FOUND_whenNotFound() {
        when(profileDao.findByIdForUpdate(55L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.blacklistProfile(55L));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void blacklistProfile_setsStatusBLACKLISTED_andSaves() {
        ProviderProfile profile = new ProviderProfile();
        profile.setId(5L);
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
        when(profileDao.findByIdForUpdate(5L)).thenReturn(Optional.of(profile));
        doNothing().when(profileDao).save(any(ProviderProfile.class), anyString());

        service.blacklistProfile(5L);

        ArgumentCaptor<ProviderProfile> captor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDao).save(captor.capture(), anyString());
        assertEquals(ProviderProfileStatus.BLACKLISTED.name(), captor.getValue().getStatus());
    }

    private ProviderProfile pendingProfile(long id, long userId, String businessName) {
        ProviderProfile profile = new ProviderProfile();
        profile.setId(id);
        profile.setUserId(userId);
        profile.setBusinessName(businessName);
        profile.setStatus(ProviderProfileStatus.PENDING_VERIFICATION.name());
        profile.setActive(true);
        return profile;
    }

    private Users makeUser(long id, String email, ProfileType profileType) {
        Users user = new Users();
        user.setId(id);
        user.setEmail(email);
        user.setProfileType(profileType.name());
        user.setActive(true);
        return user;
    }
}
