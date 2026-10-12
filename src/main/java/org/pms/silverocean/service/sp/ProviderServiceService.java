package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.RiskScore;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.wrappers.AddServiceRequest;
import org.pms.silverocean.service.sp.wrappers.AssignTierRequest;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.LinkedHashSet;
import org.apache.commons.lang3.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderServiceService {
    private static final java.math.BigDecimal MAX_SERVICE_UNIT_RATE = new java.math.BigDecimal("99999999.99");
    private final ProviderServiceDao serviceDao;
    private final ProviderProfileDao profileDao;
    private final ServiceCategoryDao categoryDao;
    private final ProviderDocumentDao documentDao;
    private final RefereeDao refereeDao;
    private final RiskScoreService riskScoreService;
    private final RiskScoreDao riskScoreDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final MarketplaceKycGate marketplaceKycGate;
    private final AccountDao accountDao;
    private final ServiceBookingDao bookingDao;

    public record Readiness(Set<String> uploadedDocumentTypes,Set<String> verifiedDocumentTypes,
                            Set<String> outstandingDocumentTypes,int refereeCount,int verifiedRefereeCount,int requiredReferees) {}
    private static final Set<PricingUnit> SUPPORTED_PRICING_UNITS = Set.of(
            PricingUnit.PER_HOUR, PricingUnit.PER_DAY, PricingUnit.PER_WEEK,
            PricingUnit.PER_MONTH, PricingUnit.PER_JOB, PricingUnit.FIXED);

    public Readiness serviceReadiness(long serviceId){
        var profile=profileDao.findByUserIdAndActive(userDao.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        var service=serviceDao.findByIdAndProfileId(serviceId,profile.getId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        var category=categoryDao.findById(service.getCategoryId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        Set<String> uploaded=presentDocumentTypes(service,false),verified=presentDocumentTypes(service,true),outstanding=new LinkedHashSet<>();
        if(category.getRequiredDocumentTypes()!=null)category.getRequiredDocumentTypes().forEach(type->{if(!uploaded.contains(type.name()))outstanding.add(type.name());});
        return new Readiness(uploaded,verified,outstanding,refereeDao.countByProfileId(profile.getId()),refereeDao.countVerifiedByProfileId(profile.getId()),category.getRequiredNumberOfReferees());
    }

    private Set<String> presentDocumentTypes(ProviderService service,boolean verified){
        Set<String> own=verified?documentDao.findVerifiedDocumentTypesByServiceId(service.getId()):documentDao.findUploadedDocumentTypesByServiceId(service.getId());
        Set<String> result=new LinkedHashSet<>(own==null?Set.of():own);
        Set<String> reused=verified?documentDao.findReusableVerifiedDocumentTypes(service.getProfileId(),service.getCategoryId()):documentDao.findReusableUploadedDocumentTypes(service.getProfileId(),service.getCategoryId());
        if(reused!=null)result.addAll(reused);
        profileDao.findById(service.getProfileId()).ifPresent(profile->{
            Set<String> common=marketplaceKycGate.currentVerifiedDocuments(profile.getUserId()).stream().map(d->d.getDocumentType()).collect(java.util.stream.Collectors.toSet());
            if(common.contains("NATIONAL_ID_FRONT")&&common.contains("NATIONAL_ID_BACK"))result.add("NATIONAL_ID");
            java.util.Map.of("PASSPORT","PASSPORT","BUSINESS_REGISTRATION_CERTIFICATE","BUSINESS_REGISTRATION","KRA_PIN_CERTIFICATE","TAX_CERTIFICATE","PROFESSIONAL_CERTIFICATE","PROFESSIONAL_CERTIFICATE","GOOD_CONDUCT_CERTIFICATE","GOOD_CONDUCT")
                    .forEach((kyc,provider)->{if(common.contains(kyc))result.add(provider);});
        });
        return result;
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderServiceDTO addService(AddServiceRequest request) {
        long userId = userDao.getUserId();
        var profile = requireApprovedProfile(userId);
        requireSupportedPricingUnit(request.pricingUnit());

        ServiceCategory category = categoryDao.findById(request.categoryId())
                .filter(ServiceCategory::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        ProviderService service = new ProviderService();
        service.setProfileId(profile.getId());
        service.setCategoryId(category.getId());
        service.setCategoryName(category.getName());
        applyCatalogueDetails(service, request, category);
        service.setAmount(normalizedServiceAmount(request.amount()));
        service.setCurrency(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(request.currency()));
        service.setPricingUnit(request.pricingUnit());
        service.setStatus(ProviderServiceStatus.DRAFT.name());
        service.setActive(true);
        service.setCreatedBy(userId);
        serviceDao.save(service, Permission.ADD_SP_SERVICE);

        riskScoreService.initRiskScore(service.getId());

        return toDTO(service);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderServiceDTO editService(long serviceId, AddServiceRequest request) {
        long userId = userDao.getUserId();
        var profile = requireApprovedProfile(userId);
        requireSupportedPricingUnit(request.pricingUnit());

        ProviderService service = serviceDao.findOwnedForUpdate(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));

        if (!Set.of(ProviderServiceStatus.DRAFT.name(), ProviderServiceStatus.HIDDEN.name(),
                ProviderServiceStatus.APPROVED.name()).contains(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }
        if (ProviderServiceStatus.HIDDEN.name().equals(service.getStatus())
                && bookingDao.existsNonTerminalBookingForService(service.getId())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_HAS_ACTIVE_WORK);
        }

        ServiceCategory category = categoryDao.findById(request.categoryId())
                .filter(ServiceCategory::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        service.setCategoryId(category.getId());
        service.setCategoryName(category.getName());
        applyCatalogueDetails(service, request, category);
        service.setAmount(normalizedServiceAmount(request.amount()));
        service.setCurrency(org.pms.silverocean.service.payment.money.MonetaryPolicy.currency(request.currency()));
        service.setPricingUnit(request.pricingUnit());
        service.setStatus(ProviderServiceStatus.DRAFT.name());
        serviceDao.save(service, Permission.EDIT_SP_SERVICE);

        return toDTO(service);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void submitForReview(long serviceId) {
        long userId = userDao.getUserId();
        var profile = requireApprovedProfile(userId);

        ProviderService service = serviceDao.findOwnedForUpdate(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));

        if (!ProviderServiceStatus.DRAFT.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }
        requireSupportedPricingUnit(service.getPricingUnit());

        validateReadiness(service, false);

        categoryDao.findById(service.getCategoryId()).filter(ServiceCategory::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        service.setStatus(ProviderServiceStatus.SUBMITTED.name());
        serviceDao.save(service, Permission.EDIT_SP_SERVICE);
    }

    public Page<ProviderServiceDTO> listMyServices(Pageable pageable) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return serviceDao.findByProfileIdEnriched(profile.getId(), pageable);
    }

    public Page<ProviderServiceDTO> listPendingAdminReview(Pageable pageable) {
        return serviceDao.findPendingAdminReviewEnriched(pageable);
    }

    public record AdminReview(ProviderServiceDTO service, Readiness readiness, java.util.List<String> outstandingMatrixRequirements) {}

    public AdminReview adminReview(long serviceId) {
        ProviderService service = serviceDao.findById(serviceId).filter(ProviderService::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        var category = categoryDao.findById(service.getCategoryId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        Set<String> uploaded = presentDocumentTypes(service, false), verified = presentDocumentTypes(service, true);
        Set<String> outstanding = new LinkedHashSet<>();
        if (category.getRequiredDocumentTypes() != null) category.getRequiredDocumentTypes().forEach(type -> {
            if (!verified.contains(type.name())) outstanding.add(type.name());
        });
        var profile=profileDao.findById(service.getProfileId()).filter(ProviderProfile::isActive).orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        var user=userDao.findById(profile.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        var type=org.pms.silverocean.service.users.ProfileType.valueOf(user.getProfileType());
        var missingMatrix=new java.util.LinkedHashSet<String>(marketplaceKycGate.missingRequirements(profile.getUserId(),"PROVIDER_TYPE","SERVICE_PROVIDER",type));
        missingMatrix.addAll(marketplaceKycGate.missingRequirements(profile.getUserId(),"SERVICE_CATEGORY",String.valueOf(service.getCategoryId()),type));
        return new AdminReview(toDTO(service), new Readiness(uploaded, verified, outstanding,
                refereeDao.countByProfileId(service.getProfileId()), refereeDao.countVerifiedByProfileId(service.getProfileId()),
                category.getRequiredNumberOfReferees()),java.util.List.copyOf(missingMatrix));
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void approveService(long serviceId, String adminNotes) {
        ProviderService service = serviceDao.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!ProviderServiceStatus.SUBMITTED.name().equals(service.getStatus()) && !ProviderServiceStatus.UNDER_REVIEW.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_CANNOT_APPROVE);
        }
        validateReadiness(service, true);
        requireSupportedPricingUnit(service.getPricingUnit());
        service.setStatus(ProviderServiceStatus.APPROVED.name());
        serviceDao.save(service, Permission.APPROVE_SP_SERVICE);
        riskScoreService.setVerified(serviceId);
        sendServiceNotification(service, NotificationType.SP_SERVICE_APPROVED_EMAIL, adminNotes);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderServiceDTO publishService(long serviceId) {
        long userId = userDao.getUserId();
        ProviderProfile profile = requireApprovedProfile(userId);
        ProviderService service = serviceDao.findOwnedForUpdate(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!service.isActive()) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }
        if (ProviderServiceStatus.LISTED.name().equals(service.getStatus())) return toDTO(service);
        if (!ProviderServiceStatus.APPROVED.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }
        requireSupportedPricingUnit(service.getPricingUnit());
        validateReadiness(service, true);
        categoryDao.findById(service.getCategoryId()).filter(ServiceCategory::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        service.setStatus(ProviderServiceStatus.LISTED.name());
        serviceDao.save(service, Permission.EDIT_SP_SERVICE);
        return toDTO(service);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void rejectService(long serviceId, String adminNotes) {
        ProviderService service = serviceDao.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if(!Set.of(ProviderServiceStatus.SUBMITTED.name(),ProviderServiceStatus.UNDER_REVIEW.name()).contains(service.getStatus())||org.apache.commons.lang3.StringUtils.isBlank(adminNotes))throw new PMSCustomException(ResponseCode.SP_SERVICE_CANNOT_APPROVE);
        service.setStatus(ProviderServiceStatus.DRAFT.name());
        serviceDao.save(service, Permission.APPROVE_SP_SERVICE);
        sendServiceNotification(service, NotificationType.SP_SERVICE_REJECTED_EMAIL, adminNotes);
    }

    private void requireSupportedPricingUnit(PricingUnit pricingUnit) {
        if (!SUPPORTED_PRICING_UNITS.contains(pricingUnit)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Choose per hour, per day, per week, per month, per job or fixed pricing.");
        }
    }

    private java.math.BigDecimal normalizedServiceAmount(java.math.BigDecimal amount) {
        try {
            java.math.BigDecimal normalized = org.pms.silverocean.service.payment.money.MonetaryPolicy.positive(amount);
            if (normalized.compareTo(MAX_SERVICE_UNIT_RATE) > 0) {
                throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                        "The service unit price is above the supported transaction limit.");
            }
            return normalized;
        } catch (PMSCustomException known) {
            throw known;
        } catch (IllegalArgumentException invalidAmount) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Enter a valid service unit price.");
        }
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void suspendService(long serviceId, String reason) {
        ProviderService service = serviceDao.findAnyByIdForUpdate(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (ProviderServiceStatus.SUSPENDED.name().equals(service.getStatus())) return;
        if (org.apache.commons.lang3.StringUtils.isBlank(reason)) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_CANNOT_APPROVE);
        }
        service.setStatus(ProviderServiceStatus.SUSPENDED.name());
        serviceDao.save(service, Permission.SUSPEND_SP_SERVICE);
        sendServiceNotification(service, NotificationType.SP_SERVICE_SUSPENDED_EMAIL, reason);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void removeService(long serviceId) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        ProviderService service = serviceDao.findOwnedForUpdate(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (bookingDao.existsNonTerminalBookingForService(serviceId)
                || bookingDao.existsActiveUnpaidInvoiceForService(serviceId)) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_HAS_ACTIVE_WORK);
        }
        service.setStatus(ProviderServiceStatus.REMOVED.name());
        service.setActive(false);
        serviceDao.save(service, Permission.REMOVE_SP_SERVICE);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void assignTier(long serviceId, AssignTierRequest request) {
        ProviderService service = serviceDao.findByIdForUpdate(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        service.setTier(serviceDao.requireActiveTier(request.tier()));
        serviceDao.save(service, Permission.ASSIGN_SP_TIER);
    }

    private void validateReadiness(ProviderService service, boolean requireVerified) {
        if (StringUtils.isBlank(service.getTitle()) || StringUtils.length(StringUtils.trim(service.getTitle())) < 3
                || StringUtils.isBlank(service.getDescription()) || StringUtils.length(StringUtils.trim(service.getDescription())) < 20) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,
                    "Add a clear service name and description before submitting it for review.");
        }
        ServiceCategory category = categoryDao.findById(service.getCategoryId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        if(requireVerified){
            var profile=profileDao.findById(service.getProfileId()).filter(ProviderProfile::isActive).orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
            if (!"ACTIVE".equals(profile.getStatus()) || profile.getPaymentAccountId() == null) {
                throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
            }
            var paymentAccount = accountDao.getAccountByIdAndCreatedBy(profile.getPaymentAccountId(), profile.getUserId());
            if (!paymentAccount.isActive() || !paymentAccount.isVerified()
                    || paymentAccount.getCategory() != AccountCategory.MERCHANT || paymentAccount.getChannel() == null
                    || paymentAccount.getChannel() == org.pms.silverocean.service.payment.wrappers.PaymentChannel.FLUTTER_WAVE) {
                throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
            }
            var user=userDao.findById(profile.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
            var type=org.pms.silverocean.service.users.ProfileType.valueOf(user.getProfileType());
            marketplaceKycGate.require(profile.getUserId(),"PROVIDER_TYPE","SERVICE_PROVIDER",type);
            marketplaceKycGate.require(profile.getUserId(),"SERVICE_CATEGORY",String.valueOf(service.getCategoryId()),type);
        }

        Set<DocumentType> required = category.getRequiredDocumentTypes();
        if (required != null && !required.isEmpty()) {
            Set<String> resolvedPresentTypes = presentDocumentTypes(service,requireVerified);
            boolean allPresent = required.stream().allMatch(dt -> resolvedPresentTypes.contains(dt.name()));
            if (!allPresent) {
                throw new PMSCustomException(ResponseCode.SP_SERVICE_MISSING_REQUIRED_DOCUMENTS);
            }
        }

        if (category.getRequiredNumberOfReferees() > 0) {
            int refereeCount = requireVerified
                    ? refereeDao.countVerifiedByProfileId(service.getProfileId())
                    : refereeDao.countByProfileId(service.getProfileId());
            if (refereeCount < category.getRequiredNumberOfReferees()) {
                throw new PMSCustomException(ResponseCode.SP_SERVICE_INSUFFICIENT_VERIFIED_REFEREES);
            }
        }
    }

    @Transactional(transactionManager="pmsDBTransactionManager")
    public ProviderServiceDTO pauseOrResume(long serviceId,boolean pause){
        var profile=profileDao.findByUserIdAndActive(userDao.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        var service=serviceDao.findOwnedForUpdate(serviceId,profile.getId()).orElseThrow(()->new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if(!service.isActive()||!(pause?ProviderServiceStatus.LISTED:ProviderServiceStatus.HIDDEN).name().equals(service.getStatus()))throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        if(!pause)validateReadiness(service,true);
        service.setStatus((pause?ProviderServiceStatus.HIDDEN:ProviderServiceStatus.LISTED).name());serviceDao.save(service,Permission.EDIT_SP_SERVICE);return toDTO(service);
    }

    private ProviderServiceDTO toDTO(ProviderService service) {
        ProviderProfile profile = profileDao.findById(service.getProfileId()).orElse(null);
        String providerName = profile != null ? profile.getBusinessName() : null;
        Double latitude = profile != null ? profile.getLatitude() : null;
        Double longitude = profile != null ? profile.getLongitude() : null;
        String riskLabel = riskScoreDao.findLatestByServiceId(service.getId())
                .map(RiskScore::getLabel).orElse(null);
        return new ProviderServiceDTO(service, providerName, latitude, longitude, riskLabel);
    }

    private void applyCatalogueDetails(ProviderService service, AddServiceRequest request, ServiceCategory category) {
        String title = StringUtils.trimToNull(request.title());
        String description = StringUtils.trimToNull(request.description());
        service.setTitle(StringUtils.left(title != null ? title : category.getName(), 120));
        String defaultDescription = StringUtils.defaultIfBlank(category.getDescription(),
                "Professional " + category.getName().toLowerCase(java.util.Locale.ROOT)
                        + " service. Contact the provider to confirm the scope and timing.");
        service.setDescription(StringUtils.left(description != null ? description : defaultDescription, 2000));
    }

    private ProviderProfile requireApprovedProfile(long userId) {
        ProviderProfile profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        if (!org.pms.silverocean.service.sp.enums.ProviderProfileStatus.ACTIVE.name().equals(profile.getStatus())) {
            throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        }
        return profile;
    }

    private void sendServiceNotification(ProviderService service, NotificationType type, String extra) {
        try {
            profileDao.findById(service.getProfileId()).ifPresent(profile -> {
                userDao.findById(profile.getUserId()).ifPresent(user -> {
                    String message = String.format(
                            i18NService.getLocalizedMessage(type.getBody()),
                            org.springframework.web.util.HtmlUtils.htmlEscape(service.getCategoryName()),
                            org.springframework.web.util.HtmlUtils.htmlEscape(extra != null ? extra : "")
                    );
                    notificationService.queueEmailAndInApp(user.getEmail(),type,message,"SERVICE_LISTING_REVIEW","Your service listing review was updated. Open /dashboard/services to review its status and any next step.");
                });
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not queue the service review update",e);
        }
    }
}
