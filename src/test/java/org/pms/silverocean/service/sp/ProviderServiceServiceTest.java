package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.AddServiceRequest;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceServiceTest {

    @Mock
    private ProviderServiceDao serviceDao;
    @Mock
    private ProviderProfileDao profileDao;
    @Mock
    private ServiceCategoryDao categoryDao;
    @Mock
    private ProviderDocumentDao documentDao;
    @Mock
    private RefereeDao refereeDao;
    @Mock
    private RiskScoreService riskScoreService;
    @Mock
    private RiskScoreDao riskScoreDao;
    @Mock
    private UserDao userDao;
    @Mock
    private NotificationService notificationService;
    @Mock
    private I18NService i18NService;
    @Mock private org.pms.silverocean.service.kyc.MarketplaceKycGate marketplaceKycGate;
    @Mock private AccountDao accountDao;
    @Mock private ServiceBookingDao bookingDao;

    private ProviderServiceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProviderServiceService(serviceDao, profileDao, categoryDao, documentDao,
                refereeDao, riskScoreService, riskScoreDao, userDao, notificationService, i18NService,
                marketplaceKycGate, accountDao, bookingDao);
        ProviderProfile approvedProfile=makeProfile(10L,1L);
        approvedProfile.setActive(true);
        approvedProfile.setStatus("ACTIVE");
        approvedProfile.setPaymentAccountId(40L);
        when(profileDao.findById(10L)).thenReturn(Optional.of(approvedProfile));
        var user=new org.pms.silverocean.database.pms.entities.Users();user.setId(1L);
        when(userDao.findById(1L)).thenReturn(Optional.of(user));
        PaymentAccount account=new PaymentAccount();account.setId(40L);account.setActive(true);account.setVerified(true);
        account.setCategory(AccountCategory.MERCHANT);account.setChannel(PaymentChannel.MPESA);
        when(accountDao.getAccountByIdAndCreatedBy(40L,1L)).thenReturn(account);
    }

    private ProviderProfile makeProfile(long id, long userId) {
        ProviderProfile p = new ProviderProfile();
        p.setId(id);
        p.setUserId(userId);
        p.setBusinessName("Test Provider");
        p.setLatitude(-1.286389);
        p.setLongitude(36.817223);
        p.setActive(true);
        p.setStatus("ACTIVE");
        return p;
    }

    private RiskScore makeRiskScore(long serviceId, String label) {
        RiskScore r = new RiskScore();
        r.setServiceId(serviceId);
        r.setLabel(label);
        return r;
    }

    private ServiceCategory makeCategory(long id, String name) {
        ServiceCategory c = new ServiceCategory();
        c.setId(id);
        c.setName(name);
        c.setDescription("Professional service delivered with clear scope and care.");
        c.setActive(true);
        return c;
    }

    private ProviderService makeService(long id, long profileId, String status) {
        ProviderService s = new ProviderService();
        s.setId(id);
        s.setProfileId(profileId);
        s.setStatus(status);
        s.setCategoryName("Cleaning");
        s.setTitle("Professional cleaning");
        s.setDescription("A clearly described professional cleaning service.");
        s.setPricingUnit(PricingUnit.PER_JOB);
        s.setActive(true);
        return s;
    }

    // --- addService ---
    @Test void retiredCategoryCannotAcceptNewService(){
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L,1L)));
        var category=makeCategory(2L,"Retired");category.setActive(false);
        when(categoryDao.findById(2L)).thenReturn(Optional.of(category));
        var ex=assertThrows(PMSCustomException.class,()->service.addService(new AddServiceRequest(2L,BigDecimal.TEN,"KES",PricingUnit.PER_JOB)));
        assertEquals(ResponseCode.SP_CATEGORY_NOT_FOUND,ex.getResponseCode());
        org.mockito.Mockito.verify(serviceDao,org.mockito.Mockito.never()).save(any(),anyString());
    }
    @Test void adminReviewUsesVerifiedEvidenceAndConfirmedReferees(){
        var current=makeService(7L,10L,ProviderServiceStatus.SUBMITTED.name());current.setCategoryId(2L);current.setActive(true);
        var category=makeCategory(2L,"Cleaning");category.setRequiredDocumentTypes(Set.of(DocumentType.GOOD_CONDUCT,DocumentType.NATIONAL_ID));category.setRequiredNumberOfReferees(2);
        when(serviceDao.findById(7L)).thenReturn(Optional.of(current));when(categoryDao.findById(2L)).thenReturn(Optional.of(category));
        when(documentDao.findUploadedDocumentTypesByServiceId(7L)).thenReturn(Set.of("GOOD_CONDUCT"));
        when(documentDao.findReusableVerifiedDocumentTypes(10L,2L)).thenReturn(Set.of("NATIONAL_ID"));
        when(refereeDao.countByProfileId(10L)).thenReturn(2);when(refereeDao.countVerifiedByProfileId(10L)).thenReturn(1);
        var review=service.adminReview(7L);
        assertEquals(Set.of("GOOD_CONDUCT"),review.readiness().outstandingDocumentTypes());
        assertEquals(1,review.readiness().verifiedRefereeCount());assertEquals(2,review.readiness().requiredReferees());
    }

    @Test
    void addService_throwsSP_PROFILE_NOT_FOUND_whenNoProfile() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_HOUR);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.addService(request));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void addService_rejectsProfileStillPendingAdminApproval() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile pending = makeProfile(10L, 1L);
        pending.setStatus("PENDING_VERIFICATION");
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(pending));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.addService(new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_HOUR)));

        assertEquals(ResponseCode.FORBIDDEN_ACCESS, ex.getResponseCode());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    @Test
    void addService_throwsSP_CATEGORY_NOT_FOUND_whenCategoryMissing() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(categoryDao.findById(99L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(99L, BigDecimal.TEN, "KES", PricingUnit.PER_JOB);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.addService(request));
        assertEquals(ResponseCode.SP_CATEGORY_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void addService_savesWithDraftStatus_andCallsInitRiskScore_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = makeProfile(10L, 1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(profile));
        when(categoryDao.findById(2L)).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));
        doAnswer(inv -> { ((ProviderService) inv.getArgument(0)).setId(1L); return null; })
                .when(serviceDao).save(any(ProviderService.class), anyString());
        doNothing().when(riskScoreService).initRiskScore(anyLong());
        when(profileDao.findById(10L)).thenReturn(Optional.of(profile));
        when(riskScoreDao.findLatestByServiceId(1L)).thenReturn(Optional.of(makeRiskScore(1L, "VERIFIED")));

        AddServiceRequest request = new AddServiceRequest(2L, new BigDecimal("500.00"), "KES", PricingUnit.PER_JOB);

        ProviderServiceDTO dto = service.addService(request);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        ProviderService saved = captor.getValue();

        assertEquals(ProviderServiceStatus.DRAFT.name(), saved.getStatus());
        assertEquals("Plumbing", saved.getTitle());
        assertEquals("Professional service delivered with clear scope and care.", saved.getDescription());
        assertEquals("Plumbing", saved.getCategoryName());
        assertEquals(10L, saved.getProfileId());
        verify(riskScoreService).initRiskScore(anyLong());
        assertEquals(ProviderServiceStatus.DRAFT.name(), dto.status());
        assertEquals("Test Provider", dto.serviceProviderName());
        assertEquals(-1.286389, dto.latitude());
        assertEquals(36.817223, dto.longitude());
        assertEquals("VERIFIED", dto.riskLabel());
    }

    // --- editService ---

    @Test
    void editService_throwsSP_PROFILE_NOT_FOUND_whenNoProfile() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_JOB);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(1L, request));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editService_throwsSP_SERVICE_NOT_FOUND_whenServiceNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findOwnedForUpdate(5L, 10L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_JOB);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(5L, request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editService_throwsSP_SERVICE_NOT_EDITABLE_whenStatusIsNotDraft() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(5L, 10L, ProviderServiceStatus.LISTED.name());
        when(serviceDao.findOwnedForUpdate(5L, 10L)).thenReturn(Optional.of(s));

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_JOB);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(5L, request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_EDITABLE, ex.getResponseCode());
    }

    @Test
    void editService_blocksHiddenListingWhileBookingIsStillOpen() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService listing = makeService(5L, 10L, ProviderServiceStatus.HIDDEN.name());
        when(serviceDao.findOwnedForUpdate(5L, 10L)).thenReturn(Optional.of(listing));
        when(bookingDao.existsNonTerminalBookingForService(5L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(5L,
                        new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_JOB)));

        assertEquals(ResponseCode.SP_SERVICE_HAS_ACTIVE_WORK, ex.getResponseCode());
        assertEquals(ProviderServiceStatus.HIDDEN.name(), listing.getStatus());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
        org.mockito.Mockito.verify(categoryDao, org.mockito.Mockito.never()).findById(anyLong());
    }

    @Test
    void editService_allowsHiddenListingAfterAllBookingsAreTerminal() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = makeProfile(10L, 1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(profile));
        ProviderService listing = makeService(5L, 10L, ProviderServiceStatus.HIDDEN.name());
        when(serviceDao.findOwnedForUpdate(5L, 10L)).thenReturn(Optional.of(listing));
        when(bookingDao.existsNonTerminalBookingForService(5L)).thenReturn(false);
        when(categoryDao.findById(3L)).thenReturn(Optional.of(makeCategory(3L, "Gardening")));

        service.editService(5L,
                new AddServiceRequest(3L, new BigDecimal("200.00"), "KES", PricingUnit.PER_JOB));

        assertEquals(ProviderServiceStatus.DRAFT.name(), listing.getStatus());
        verify(serviceDao).save(listing, "edit_sp_service");
    }

    @Test
    void editService_updatesFieldsAndSaves_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = makeProfile(10L, 1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(profile));
        ProviderService s = makeService(5L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findOwnedForUpdate(5L, 10L)).thenReturn(Optional.of(s));
        when(categoryDao.findById(3L)).thenReturn(Optional.of(makeCategory(3L, "Gardening")));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(10L)).thenReturn(Optional.of(profile));
        when(riskScoreDao.findLatestByServiceId(5L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(3L, new BigDecimal("200.00"), "USD", PricingUnit.PER_JOB);

        ProviderServiceDTO dto = service.editService(5L, request);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        ProviderService saved = captor.getValue();

        assertEquals("Gardening", saved.getCategoryName());
        assertEquals(new BigDecimal("200.00"), saved.getAmount());
        assertEquals("USD", saved.getCurrency());
        assertEquals(PricingUnit.PER_JOB, saved.getPricingUnit());
    }

    @Test
    void addService_rejectsVariableUnitUntilQuantityAndQuoteAcceptanceAreSupported() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.addService(
                new AddServiceRequest(3L, new BigDecimal("200.00"), "KES", PricingUnit.PER_HOUR)));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, error.getResponseCode());
        verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    // --- submitForReview ---

    @Test
    void submitForReview_throwsSP_SERVICE_NOT_FOUND_whenServiceNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void submitForReview_throwsSP_SERVICE_NOT_EDITABLE_whenNotDraft() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.of(s));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_EDITABLE, ex.getResponseCode());
    }

    @Test
    void submitForReview_setsStatusSUBMITTED_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.of(s));
        // categoryId is 0 (default) → categoryDao returns empty → validateReadiness skips
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));

        service.submitForReview(7L);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.SUBMITTED.name(), captor.getValue().getStatus());
    }

    @Test
    void submitForReview_rejectsIncompleteCatalogueDetails() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService listing = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        listing.setDescription("Too short");
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.of(listing));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.submitForReview(7L));

        assertEquals(ResponseCode.INVALID_FIELD_DATA, ex.getResponseCode());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    @Test
    void submitForReview_throwsSP_SERVICE_MISSING_REQUIRED_DOCUMENTS_whenDocumentNotUploaded() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        s.setCategoryId(2L);
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.of(s));
        ServiceCategory category = makeCategory(2L, "Plumbing");
        category.setRequiredDocumentTypes(Set.of(DocumentType.NATIONAL_ID, DocumentType.GOOD_CONDUCT));
        when(categoryDao.findById(2L)).thenReturn(Optional.of(category));
        // uses uploaded (not verified) query — GOOD_CONDUCT not yet uploaded
        when(documentDao.findUploadedDocumentTypesByServiceId(7L)).thenReturn(Set.of("NATIONAL_ID"));

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_MISSING_REQUIRED_DOCUMENTS, ex.getResponseCode());
    }

    @Test
    void submitForReview_throwsSP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES_whenRefereesInsufficient() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        s.setCategoryId(2L);
        when(serviceDao.findOwnedForUpdate(7L, 10L)).thenReturn(Optional.of(s));
        ServiceCategory category = makeCategory(2L, "Plumbing");
        category.setRequiredNumberOfReferees(2);
        when(categoryDao.findById(2L)).thenReturn(Optional.of(category));
        // uses total count (not verified) query — only 1 referee added, need 2
        when(refereeDao.countByProfileId(10L)).thenReturn(1);

        PMSCustomException ex = assertThrows(PMSCustomException.class, () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES, ex.getResponseCode());
    }

    // --- approveService ---

    @Test
    void approveService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "Looks good"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void approveService_throwsSP_SERVICE_CANNOT_APPROVE_whenStatusIsDraft() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(s));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "notes"));
        assertEquals(ResponseCode.SP_SERVICE_CANNOT_APPROVE, ex.getResponseCode());
    }

    @Test
    void approveService_setsStatusLISTED_andCallsSetVerified_onSuccess() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        doNothing().when(riskScoreService).setVerified(8L);
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Your service %s was approved. %s");

        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));
        service.approveService(8L, "Well done");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.LISTED.name(), captor.getValue().getStatus());
        verify(riskScoreService).setVerified(8L);
    }

    @Test
    void approveService_rejectsProviderProfileThatIsStillPendingApproval() {
        ProviderService listing = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(listing));
        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));
        ProviderProfile pending = makeProfile(10L, 1L);
        pending.setActive(true);
        pending.setStatus("PENDING_VERIFICATION");
        pending.setPaymentAccountId(40L);
        when(profileDao.findById(10L)).thenReturn(Optional.of(pending));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "Looks good"));

        assertEquals(ResponseCode.ACCOUNT_NOT_FOUND, ex.getResponseCode());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    @Test
    void approveService_throwsSP_SERVICE_MISSING_REQUIRED_DOCUMENTS_whenDocumentNotVerified() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        s.setCategoryId(3L);
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(s));
        ServiceCategory category = makeCategory(3L, "Electrical");
        category.setRequiredDocumentTypes(Set.of(DocumentType.PROFESSIONAL_CERTIFICATE));
        when(categoryDao.findById(3L)).thenReturn(Optional.of(category));
        when(documentDao.findVerifiedDocumentTypesByServiceId(8L)).thenReturn(Set.of()); // none verified

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "notes"));
        assertEquals(ResponseCode.SP_SERVICE_MISSING_REQUIRED_DOCUMENTS, ex.getResponseCode());
    }

    @Test
    void approveService_throwsSP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES_whenRefereesInsufficient() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        s.setCategoryId(3L);
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(s));
        ServiceCategory category = makeCategory(3L, "Electrical");
        category.setRequiredNumberOfReferees(3);
        when(categoryDao.findById(3L)).thenReturn(Optional.of(category));
        when(refereeDao.countVerifiedByProfileId(10L)).thenReturn(2); // 2 confirmed, need 3

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "notes"));
        assertEquals(ResponseCode.SP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES, ex.getResponseCode());
    }

    // --- rejectService ---

    @Test void approvalCannotBypassKycWhenProviderProfileIsMissing(){
        ProviderService s=makeService(8L,10L,ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findByIdForUpdate(8L)).thenReturn(Optional.of(s));
        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L,"Cleaning")));
        when(profileDao.findById(10L)).thenReturn(Optional.empty());
        var ex=assertThrows(PMSCustomException.class,()->service.approveService(8L,"Reviewed"));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND,ex.getResponseCode());
    }

    @Test
    void rejectService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(serviceDao.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rejectService(9L, "does not meet standards"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void rejectService_returnsEditableDraft_onSuccess() {
        ProviderService s = makeService(9L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findByIdForUpdate(9L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Service %s rejected. %s");

        service.rejectService(9L, "Not compliant");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.DRAFT.name(), captor.getValue().getStatus());
    }

    // --- suspendService ---

    @Test
    void suspendService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(serviceDao.findAnyByIdForUpdate(11L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.suspendService(11L, "Violation"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void suspendService_setsStatusSUSPENDED_onSuccess() {
        ProviderService s = makeService(11L, 10L, ProviderServiceStatus.LISTED.name());
        when(serviceDao.findAnyByIdForUpdate(11L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Service %s suspended. %s");

        service.suspendService(11L, "Policy violation");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.SUSPENDED.name(), captor.getValue().getStatus());
    }

    @Test
    void suspendService_adminRemedyStillWorksAfterOwnerHidesListing() {
        ProviderService s = makeService(11L, 10L, ProviderServiceStatus.HIDDEN.name());
        when(serviceDao.findAnyByIdForUpdate(11L)).thenReturn(Optional.of(s));
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Service %s suspended. %s");

        service.suspendService(11L, "Complaint upheld");

        assertEquals(ProviderServiceStatus.SUSPENDED.name(), s.getStatus());
        verify(serviceDao).save(s, "suspend_sp_service");
    }

    // --- removeService ---

    @Test
    void removeService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findOwnedForUpdate(12L, 10L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.removeService(12L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void removeService_blocksListingWithOpenBooking() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService listing = makeService(12L, 10L, ProviderServiceStatus.HIDDEN.name());
        when(serviceDao.findOwnedForUpdate(12L, 10L)).thenReturn(Optional.of(listing));
        when(bookingDao.existsNonTerminalBookingForService(12L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.removeService(12L));

        assertEquals(ResponseCode.SP_SERVICE_HAS_ACTIVE_WORK, ex.getResponseCode());
        assertEquals(ProviderServiceStatus.HIDDEN.name(), listing.getStatus());
        org.mockito.Mockito.verify(bookingDao, org.mockito.Mockito.never())
                .existsActiveUnpaidInvoiceForService(12L);
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    @Test
    void removeService_blocksTerminalBookingWhoseInvoiceIsStillActiveAndUnpaid() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService listing = makeService(12L, 10L, ProviderServiceStatus.HIDDEN.name());
        when(serviceDao.findOwnedForUpdate(12L, 10L)).thenReturn(Optional.of(listing));
        when(bookingDao.existsNonTerminalBookingForService(12L)).thenReturn(false);
        when(bookingDao.existsActiveUnpaidInvoiceForService(12L)).thenReturn(true);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.removeService(12L));

        assertEquals(ResponseCode.SP_SERVICE_HAS_ACTIVE_WORK, ex.getResponseCode());
        assertEquals(ProviderServiceStatus.HIDDEN.name(), listing.getStatus());
        org.mockito.Mockito.verify(serviceDao, org.mockito.Mockito.never()).save(any(), anyString());
    }

    @Test
    void removeService_setsStatusREMOVED_andActiveFalse_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(12L, 10L, ProviderServiceStatus.LISTED.name());
        s.setActive(true);
        when(serviceDao.findOwnedForUpdate(12L, 10L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());

        service.removeService(12L);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        ProviderService saved = captor.getValue();
        assertEquals(ProviderServiceStatus.REMOVED.name(), saved.getStatus());
        assertFalse(saved.isActive());
    }
}
