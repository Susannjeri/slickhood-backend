package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.enums.ProviderProfileStatus;
import org.pms.silverocean.service.sp.wrappers.ProviderProfileDTO;
import org.pms.silverocean.service.sp.wrappers.PaymentAccountRequest;
import org.pms.silverocean.service.sp.wrappers.SetupProfileRequest;
import org.pms.silverocean.service.kyc.MarketplaceKycGate;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.payment.wrappers.PaymentChannel;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.I18NService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderProfileService {
    private final ProviderProfileDao profileDao;
    private final UserDao userDao;
    private final AccountDao accountDao;
    private final MarketplaceKycGate marketplaceKycGate;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final PaymentPlatformFactory paymentPlatformFactory;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO setupProfile(SetupProfileRequest request, String ipAddress) {
        long userId = userDao.getUserId();
        // Serialize the existence check and insert for retry/concurrency safety.
        userDao.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        if (profileDao.existsByUserId(userId)) {
            throw new PMSCustomException(ResponseCode.SP_PROFILE_ALREADY_EXISTS);
        }
        if (request.consent() == null || !request.consent()) {
            throw new PMSCustomException(ResponseCode.SP_PROFILE_CONSENT_REQUIRED);
        }
        ProviderProfile profile = new ProviderProfile();
        profile.setUserId(userId);
        profile.setBusinessName(request.businessName().trim());
        profile.setDescription(profileDescription(request));
        profile.setServiceArea(profileServiceArea(request));
        profile.setStatus(ProviderProfileStatus.PENDING_VERIFICATION.name());
        profile.setConsentTimestamp(ZonedDateTime.now(ZoneId.of("UTC")));
        profile.setConsentIpAddress(ipAddress);
        profile.setLatitude(request.latitude());
        profile.setLongitude(request.longitude());
        profile.setActive(true);
        profile.setCreatedBy(userId);
        profileDao.save(profile, Permission.SETUP_SP_PROFILE);
        return new ProviderProfileDTO(profile);
    }

    public ProviderProfileDTO getMyProfile() {
        long userId = userDao.getUserId();
        ProviderProfile profile = profileDao.findByUserId(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO editMyProfile(SetupProfileRequest request) {
        long userId=userDao.getUserId();
        ProviderProfile profile=profileDao.findByUserId(userId)
                .orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        if(ProviderProfileStatus.BLACKLISTED.name().equals(profile.getStatus())||ProviderProfileStatus.SUSPENDED.name().equals(profile.getStatus()))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        if(request.businessName()==null||request.businessName().isBlank()||request.businessName().length()>255
                ||request.latitude()==null||request.longitude()==null||!Double.isFinite(request.latitude())||!Double.isFinite(request.longitude())
                ||Math.abs(request.latitude())>90||Math.abs(request.longitude())>180)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        profile.setBusinessName(request.businessName().trim());profile.setLatitude(request.latitude());profile.setLongitude(request.longitude());
        profile.setDescription(profileDescription(request));profile.setServiceArea(profileServiceArea(request));
        profile.setStatus(ProviderProfileStatus.PENDING_VERIFICATION.name());profile.setReviewedAt(null);profile.setReviewedByUserId(null);
        // Editing contact/location details must not recreate consent, identity or payment records.
        profileDao.save(profile,Permission.EDIT_SP_PROFILE);return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO setPaymentAccount(PaymentAccountRequest request) {
        long userId = userDao.getUserId();
        ProviderProfile profile = profileDao.findByUserId(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        if(ProviderProfileStatus.BLACKLISTED.name().equals(profile.getStatus())||ProviderProfileStatus.SUSPENDED.name().equals(profile.getStatus()))throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);
        var account = accountDao.getAccountByIdAndCreatedBy(request.paymentAccountId(), userId);
        if (!isSupportedMarketplaceAccount(account)) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        profile.setPaymentAccountId(account.getId());
        profileDao.save(profile, Permission.SETUP_SP_PROFILE);
        return new ProviderProfileDTO(profile);
    }

    public Page<ProviderProfileDTO> getProfileList(Pageable pageable) {
        return profileDao.findAll(pageable).map(ProviderProfileDTO::new);
    }

    public Page<ProviderProfileDTO> getPendingProfileList(Pageable pageable) {
        return profileDao.findPending(pageable).map(ProviderProfileDTO::new);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO approveProfile(long profileId, long expectedVersion, String adminNotes) {
        ProviderProfile profile=profileDao.findByIdForUpdate(profileId)
                .orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        requireReviewVersion(profile,expectedVersion);
        if(!ProviderProfileStatus.PENDING_VERIFICATION.name().equals(profile.getStatus()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        var user=userDao.findById(profile.getUserId()).orElseThrow(()->new PMSCustomException(ResponseCode.INVALID_USER_DETAILS));
        var type=org.pms.silverocean.service.users.ProfileType.valueOf(user.getProfileType());
        marketplaceKycGate.require(profile.getUserId(),"PROVIDER_TYPE","SERVICE_PROVIDER",type);
        if(profile.getPaymentAccountId()==null)throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        var account=accountDao.getAccountByIdAndCreatedBy(profile.getPaymentAccountId(),profile.getUserId());
        if(!isSupportedMarketplaceAccount(account))throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
        applyReview(profile,adminNotes);
        profileDao.save(profile,Permission.APPROVE_SP_SERVICE);
        notifyProfileReview(profile,NotificationType.SP_PROFILE_APPROVED_EMAIL,adminNotes);
        return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO rejectProfile(long profileId, long expectedVersion, String reason) {
        ProviderProfile profile=profileDao.findByIdForUpdate(profileId)
                .orElseThrow(()->new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        requireReviewVersion(profile,expectedVersion);
        if(!ProviderProfileStatus.PENDING_VERIFICATION.name().equals(profile.getStatus())||StringUtils.isBlank(reason))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        profile.setStatus(ProviderProfileStatus.REJECTED.name());
        applyReview(profile,reason.trim());
        profileDao.save(profile,Permission.APPROVE_SP_SERVICE);
        notifyProfileReview(profile,NotificationType.SP_PROFILE_REJECTED_EMAIL,reason.trim());
        return new ProviderProfileDTO(profile);
    }

    public ProviderProfileDTO getProfileById(long profileId) {
        ProviderProfile profile = profileDao.findById(profileId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void blacklistProfile(long profileId) {
        ProviderProfile profile = profileDao.findByIdForUpdate(profileId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        profile.setStatus(ProviderProfileStatus.BLACKLISTED.name());
        profileDao.save(profile, Permission.BLACKLIST_SP);
    }

    private void applyReview(ProviderProfile profile,String notes){profile.setAdminNotes(StringUtils.left(StringUtils.trimToNull(notes),1000));profile.setReviewedByUserId(userDao.getUserId());profile.setReviewedAt(ZonedDateTime.now(ZoneId.of("UTC")));}
    private void requireReviewVersion(ProviderProfile profile,long expectedVersion){if(profile.getVersion()!=expectedVersion)throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA,"This provider profile changed after it was loaded. Refresh and review the latest details before deciding.");}
    private boolean isSupportedMarketplaceAccount(org.pms.silverocean.database.pms.entities.PaymentAccount account){return account.isActive()&&account.isVerified()&&account.getCategory()==AccountCategory.MERCHANT&&account.getChannel()!=null&&account.getChannel()!=PaymentChannel.FLUTTER_WAVE&&paymentPlatformFactory.isAvailableForNewPayments(account.getChannel());}
    private String profileDescription(SetupProfileRequest request){return StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.description()),"Professional services provided by "+request.businessName().trim()+"."),1000);}
    private String profileServiceArea(SetupProfileRequest request){return StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trimToNull(request.serviceArea()),"Location shown on the map"),255);}
    private void notifyProfileReview(ProviderProfile profile,NotificationType type,String notes){userDao.findById(profile.getUserId()).ifPresent(user->{String safeName=org.springframework.web.util.HtmlUtils.htmlEscape(profile.getBusinessName());String safeNotes=org.springframework.web.util.HtmlUtils.htmlEscape(StringUtils.defaultString(notes));String body=type==NotificationType.SP_PROFILE_APPROVED_EMAIL?String.format(i18NService.getLocalizedMessage(type.getBody()),safeName):String.format(i18NService.getLocalizedMessage(type.getBody()),safeName,safeNotes);notificationService.queueEmailAndInApp(user.getEmail(),type,body,"SERVICE_PROVIDER_PROFILE_REVIEW","Your service provider profile review was updated. Open /dashboard/services for the next step.");});}
}
