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

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO setupProfile(SetupProfileRequest request, String ipAddress) {
        long userId = userDao.getUserId();
        if (profileDao.existsByUserId(userId)) {
            throw new PMSCustomException(ResponseCode.SP_PROFILE_ALREADY_EXISTS);
        }
        if (request.consent() == null || !request.consent()) {
            throw new PMSCustomException(ResponseCode.SP_PROFILE_CONSENT_REQUIRED);
        }
        ProviderProfile profile = new ProviderProfile();
        profile.setUserId(userId);
        profile.setBusinessName(request.businessName());
        profile.setStatus(ProviderProfileStatus.ACTIVE.name());
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
        ProviderProfile profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ProviderProfileDTO setPaymentAccount(PaymentAccountRequest request) {
        long userId = userDao.getUserId();
        ProviderProfile profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        var account = accountDao.getAccountByIdAndCreatedBy(request.paymentAccountId(), userId);
        if (!account.isVerified() || !account.isActive() || account.getCategory() != AccountCategory.MERCHANT) {
            throw new PMSCustomException(ResponseCode.ACCOUNT_NOT_FOUND);
        }
        profile.setPaymentAccountId(account.getId());
        profileDao.save(profile, Permission.SETUP_SP_PROFILE);
        return new ProviderProfileDTO(profile);
    }

    public Page<ProviderProfileDTO> getProfileList(Pageable pageable) {
        return profileDao.findAll(pageable).map(ProviderProfileDTO::new);
    }

    public ProviderProfileDTO getProfileById(long profileId) {
        ProviderProfile profile = profileDao.findById(profileId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return new ProviderProfileDTO(profile);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void blacklistProfile(long profileId) {
        ProviderProfile profile = profileDao.findById(profileId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        profile.setStatus(ProviderProfileStatus.BLACKLISTED.name());
        profileDao.save(profile, Permission.BLACKLIST_SP);
    }
}
