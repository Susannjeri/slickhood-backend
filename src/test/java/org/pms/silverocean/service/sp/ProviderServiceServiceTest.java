package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.AddServiceRequest;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;

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

    private ProviderServiceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProviderServiceService(serviceDao, profileDao, categoryDao, documentDao,
                refereeDao, riskScoreService, riskScoreDao, userDao, notificationService, i18NService);
    }

    private ProviderProfile makeProfile(long id, long userId) {
        ProviderProfile p = new ProviderProfile();
        p.setId(id);
        p.setUserId(userId);
        p.setBusinessName("Test Provider");
        p.setLatitude(-1.286389);
        p.setLongitude(36.817223);
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
        return c;
    }

    private ProviderService makeService(long id, long profileId, String status) {
        ProviderService s = new ProviderService();
        s.setId(id);
        s.setProfileId(profileId);
        s.setStatus(status);
        s.setCategoryName("Cleaning");
        return s;
    }

    // --- addService ---

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
    void addService_throwsSP_CATEGORY_NOT_FOUND_whenCategoryMissing() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(categoryDao.findById(99L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(99L, BigDecimal.TEN, "KES", PricingUnit.PER_HOUR);

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

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_DAY);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(1L, request));
        assertEquals(ResponseCode.SP_PROFILE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editService_throwsSP_SERVICE_NOT_FOUND_whenServiceNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_DAY);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(5L, request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void editService_throwsSP_SERVICE_NOT_EDITABLE_whenStatusIsNotDraft() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(5L, 10L, ProviderServiceStatus.LISTED.name());
        when(serviceDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(s));

        AddServiceRequest request = new AddServiceRequest(1L, BigDecimal.TEN, "KES", PricingUnit.PER_DAY);

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.editService(5L, request));
        assertEquals(ResponseCode.SP_SERVICE_NOT_EDITABLE, ex.getResponseCode());
    }

    @Test
    void editService_updatesFieldsAndSaves_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        ProviderProfile profile = makeProfile(10L, 1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(profile));
        ProviderService s = makeService(5L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findByIdAndProfileId(5L, 10L)).thenReturn(Optional.of(s));
        when(categoryDao.findById(3L)).thenReturn(Optional.of(makeCategory(3L, "Gardening")));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(10L)).thenReturn(Optional.of(profile));
        when(riskScoreDao.findLatestByServiceId(5L)).thenReturn(Optional.empty());

        AddServiceRequest request = new AddServiceRequest(3L, new BigDecimal("200.00"), "USD", PricingUnit.PER_WEEK);

        ProviderServiceDTO dto = service.editService(5L, request);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        ProviderService saved = captor.getValue();

        assertEquals("Gardening", saved.getCategoryName());
        assertEquals(new BigDecimal("200.00"), saved.getAmount());
        assertEquals("USD", saved.getCurrency());
        assertEquals(PricingUnit.PER_WEEK, saved.getPricingUnit());
    }

    // --- submitForReview ---

    @Test
    void submitForReview_throwsSP_SERVICE_NOT_FOUND_whenServiceNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findByIdAndProfileId(7L, 10L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void submitForReview_throwsSP_SERVICE_NOT_EDITABLE_whenNotDraft() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findByIdAndProfileId(7L, 10L)).thenReturn(Optional.of(s));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.submitForReview(7L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_EDITABLE, ex.getResponseCode());
    }

    @Test
    void submitForReview_setsStatusSUBMITTED_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findByIdAndProfileId(7L, 10L)).thenReturn(Optional.of(s));
        // categoryId is 0 (default) → categoryDao returns empty → validateReadiness skips
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));

        service.submitForReview(7L);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.SUBMITTED.name(), captor.getValue().getStatus());
    }

    @Test
    void submitForReview_throwsSP_SERVICE_MISSING_REQUIRED_DOCUMENTS_whenDocumentNotUploaded() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(7L, 10L, ProviderServiceStatus.DRAFT.name());
        s.setCategoryId(2L);
        when(serviceDao.findByIdAndProfileId(7L, 10L)).thenReturn(Optional.of(s));
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
        when(serviceDao.findByIdAndProfileId(7L, 10L)).thenReturn(Optional.of(s));
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
        when(serviceDao.findById(8L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "Looks good"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void approveService_throwsSP_SERVICE_CANNOT_APPROVE_whenStatusIsDraft() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.DRAFT.name());
        when(serviceDao.findById(8L)).thenReturn(Optional.of(s));

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "notes"));
        assertEquals(ResponseCode.SP_SERVICE_CANNOT_APPROVE, ex.getResponseCode());
    }

    @Test
    void approveService_setsStatusLISTED_andCallsSetVerified_onSuccess() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findById(8L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        doNothing().when(riskScoreService).setVerified(8L);
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Your service %s was approved. %s");

        when(categoryDao.findById(anyLong())).thenReturn(Optional.of(makeCategory(2L, "Plumbing")));
        service.approveService(8L, "Well done");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.LISTED.name(), captor.getValue().getStatus());
        verify(riskScoreService).setVerified(8L);
    }

    @Test
    void approveService_throwsSP_SERVICE_MISSING_REQUIRED_DOCUMENTS_whenDocumentNotVerified() {
        ProviderService s = makeService(8L, 10L, ProviderServiceStatus.SUBMITTED.name());
        s.setCategoryId(3L);
        when(serviceDao.findById(8L)).thenReturn(Optional.of(s));
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
        when(serviceDao.findById(8L)).thenReturn(Optional.of(s));
        ServiceCategory category = makeCategory(3L, "Electrical");
        category.setRequiredNumberOfReferees(3);
        when(categoryDao.findById(3L)).thenReturn(Optional.of(category));
        when(refereeDao.countVerifiedByProfileId(10L)).thenReturn(2); // 2 confirmed, need 3

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.approveService(8L, "notes"));
        assertEquals(ResponseCode.SP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES, ex.getResponseCode());
    }

    // --- rejectService ---

    @Test
    void rejectService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(serviceDao.findById(9L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.rejectService(9L, "does not meet standards"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void rejectService_setsStatusREMOVED_onSuccess() {
        ProviderService s = makeService(9L, 10L, ProviderServiceStatus.SUBMITTED.name());
        when(serviceDao.findById(9L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Service %s rejected. %s");

        service.rejectService(9L, "Not compliant");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.REMOVED.name(), captor.getValue().getStatus());
    }

    // --- suspendService ---

    @Test
    void suspendService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(serviceDao.findById(11L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.suspendService(11L, "Violation"));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void suspendService_setsStatusSUSPENDED_onSuccess() {
        ProviderService s = makeService(11L, 10L, ProviderServiceStatus.LISTED.name());
        when(serviceDao.findById(11L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());
        when(profileDao.findById(anyLong())).thenReturn(Optional.empty());
        when(i18NService.getLocalizedMessage(anyString())).thenReturn("Service %s suspended. %s");

        service.suspendService(11L, "Policy violation");

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        assertEquals(ProviderServiceStatus.SUSPENDED.name(), captor.getValue().getStatus());
    }

    // --- removeService ---

    @Test
    void removeService_throwsSP_SERVICE_NOT_FOUND_whenNotFound() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        when(serviceDao.findByIdAndProfileId(12L, 10L)).thenReturn(Optional.empty());

        PMSCustomException ex = assertThrows(PMSCustomException.class,
                () -> service.removeService(12L));
        assertEquals(ResponseCode.SP_SERVICE_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void removeService_setsStatusREMOVED_andActiveFalse_onSuccess() {
        when(userDao.getUserId()).thenReturn(1L);
        when(profileDao.findByUserIdAndActive(1L)).thenReturn(Optional.of(makeProfile(10L, 1L)));
        ProviderService s = makeService(12L, 10L, ProviderServiceStatus.LISTED.name());
        s.setActive(true);
        when(serviceDao.findByIdAndProfileId(12L, 10L)).thenReturn(Optional.of(s));
        doNothing().when(serviceDao).save(any(ProviderService.class), anyString());

        service.removeService(12L);

        ArgumentCaptor<ProviderService> captor = ArgumentCaptor.forClass(ProviderService.class);
        verify(serviceDao).save(captor.capture(), anyString());
        ProviderService saved = captor.getValue();
        assertEquals(ProviderServiceStatus.REMOVED.name(), saved.getStatus());
        assertFalse(saved.isActive());
    }
}
