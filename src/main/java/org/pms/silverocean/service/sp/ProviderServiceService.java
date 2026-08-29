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
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.sp.dao.ProviderDocumentDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.dao.RiskScoreDao;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.ProviderServiceStatus;
import org.pms.silverocean.service.sp.wrappers.AddServiceRequest;
import org.pms.silverocean.service.sp.wrappers.AssignTierRequest;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderServiceService {
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

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderServiceDTO addService(AddServiceRequest request) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));

        ServiceCategory category = categoryDao.findById(request.categoryId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        ProviderService service = new ProviderService();
        service.setProfileId(profile.getId());
        service.setCategoryId(category.getId());
        service.setCategoryName(category.getName());
        service.setAmount(request.amount());
        service.setCurrency(request.currency());
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
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));

        ProviderService service = serviceDao.findByIdAndProfileId(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));

        if (!ProviderServiceStatus.DRAFT.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }

        ServiceCategory category = categoryDao.findById(request.categoryId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        service.setCategoryId(category.getId());
        service.setCategoryName(category.getName());
        service.setAmount(request.amount());
        service.setCurrency(request.currency());
        service.setPricingUnit(request.pricingUnit());
        serviceDao.save(service, Permission.EDIT_SP_SERVICE);

        return toDTO(service);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void submitForReview(long serviceId) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));

        ProviderService service = serviceDao.findByIdAndProfileId(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));

        if (!ProviderServiceStatus.DRAFT.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_NOT_EDITABLE);
        }

        validateReadiness(service, false);

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

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void approveService(long serviceId, String adminNotes) {
        ProviderService service = serviceDao.findById(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        if (!ProviderServiceStatus.SUBMITTED.name().equals(service.getStatus()) && !ProviderServiceStatus.UNDER_REVIEW.name().equals(service.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_SERVICE_CANNOT_APPROVE);
        }
        validateReadiness(service, true);
        service.setStatus(ProviderServiceStatus.LISTED.name());
        serviceDao.save(service, Permission.APPROVE_SP_SERVICE);
        riskScoreService.setVerified(serviceId);
        sendServiceNotification(service, NotificationType.SP_SERVICE_APPROVED_EMAIL, adminNotes);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void rejectService(long serviceId, String adminNotes) {
        ProviderService service = serviceDao.findById(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        service.setStatus(ProviderServiceStatus.REMOVED.name());
        serviceDao.save(service, Permission.APPROVE_SP_SERVICE);
        sendServiceNotification(service, NotificationType.SP_SERVICE_REJECTED_EMAIL, adminNotes);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void suspendService(long serviceId, String reason) {
        ProviderService service = serviceDao.findById(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        service.setStatus(ProviderServiceStatus.SUSPENDED.name());
        serviceDao.save(service, Permission.SUSPEND_SP_SERVICE);
        sendServiceNotification(service, NotificationType.SP_SERVICE_SUSPENDED_EMAIL, reason);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void removeService(long serviceId) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        ProviderService service = serviceDao.findByIdAndProfileId(serviceId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        service.setStatus(ProviderServiceStatus.REMOVED.name());
        service.setActive(false);
        serviceDao.save(service, Permission.REMOVE_SP_SERVICE);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void assignTier(long serviceId, AssignTierRequest request) {
        ProviderService service = serviceDao.findById(serviceId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        service.setTier(request.tier());
        serviceDao.save(service, Permission.ASSIGN_SP_TIER);
    }

    private void validateReadiness(ProviderService service, boolean requireVerified) {
        ServiceCategory category = categoryDao.findById(service.getCategoryId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));

        Set<DocumentType> required = category.getRequiredDocumentTypes();
        if (required != null && !required.isEmpty()) {
            Set<String> presentTypes = requireVerified
                    ? documentDao.findVerifiedDocumentTypesByServiceId(service.getId())
                    : documentDao.findUploadedDocumentTypesByServiceId(service.getId());
            boolean allPresent = required.stream().allMatch(dt -> presentTypes.contains(dt.name()));
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

    private ProviderServiceDTO toDTO(ProviderService service) {
        ProviderProfile profile = profileDao.findById(service.getProfileId()).orElse(null);
        String providerName = profile != null ? profile.getBusinessName() : null;
        Double latitude = profile != null ? profile.getLatitude() : null;
        Double longitude = profile != null ? profile.getLongitude() : null;
        String riskLabel = riskScoreDao.findLatestByServiceId(service.getId())
                .map(RiskScore::getLabel).orElse(null);
        return new ProviderServiceDTO(service, providerName, latitude, longitude, riskLabel);
    }

    private void sendServiceNotification(ProviderService service, NotificationType type, String extra) {
        try {
            profileDao.findById(service.getProfileId()).ifPresent(profile -> {
                userDao.findById(profile.getUserId()).ifPresent(user -> {
                    String message = String.format(
                            i18NService.getLocalizedMessage(type.getBody()),
                            service.getCategoryName(),
                            extra != null ? extra : ""
                    );
                    notificationService.sendNotification(new NotificationDTO(message, user.getEmail(), type));
                });
            });
        } catch (Exception e) {
            log.warn("Failed to send service notification for service {}", service.getId(), e);
        }
    }
}
