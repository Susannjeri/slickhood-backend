package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Referee;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.RefereeDao;
import org.pms.silverocean.service.sp.enums.RefereeStatus;
import org.pms.silverocean.service.sp.wrappers.AddRefereeRequest;
import org.pms.silverocean.service.sp.wrappers.RefereeDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefereeService {
    private final RefereeDao refereeDao;
    private final ProviderProfileDao profileDao;
    private final UserDao userDao;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public RefereeDTO addReferee(AddRefereeRequest request) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        Referee referee = new Referee();
        referee.setProfileId(profile.getId());
        referee.setName(request.name());
        referee.setContact(request.contact());
        referee.setVerificationStatus(RefereeStatus.PENDING.name());
        referee.setActive(true);
        referee.setCreatedBy(userId);
        refereeDao.save(referee, Permission.ADD_SP_REFEREE);
        return new RefereeDTO(referee);
    }

    public Page<RefereeDTO> listMyReferees(Pageable pageable) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        return refereeDao.findByProfileId(profile.getId(), pageable).map(RefereeDTO::new);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public RefereeDTO editReferee(long refereeId, AddRefereeRequest request) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        Referee referee = refereeDao.findByIdAndProfileId(refereeId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_REFEREE_NOT_FOUND));
        if (!RefereeStatus.PENDING.name().equals(referee.getVerificationStatus())) {
            throw new PMSCustomException(ResponseCode.SP_REFEREE_CANNOT_EDIT);
        }
        referee.setName(request.name());
        referee.setContact(request.contact());
        refereeDao.save(referee, Permission.EDIT_SP_REFEREE);
        return new RefereeDTO(referee);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void removeReferee(long refereeId) {
        long userId = userDao.getUserId();
        var profile = profileDao.findByUserIdAndActive(userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        Referee referee = refereeDao.findByIdAndProfileId(refereeId, profile.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_REFEREE_NOT_FOUND));
        if (!RefereeStatus.PENDING.name().equals(referee.getVerificationStatus())) {
            throw new PMSCustomException(ResponseCode.SP_REFEREE_CANNOT_EDIT);
        }
        referee.setActive(false);
        refereeDao.save(referee, Permission.REMOVE_SP_REFEREE);
    }

    public Page<RefereeDTO> listRefereesForProfile(long profileId, Pageable pageable) {
        return refereeDao.findByProfileId(profileId, pageable).map(RefereeDTO::new);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void verifyReferee(long refereeId, RefereeStatus status) {
        Referee referee = refereeDao.findById(refereeId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_REFEREE_NOT_FOUND));
        referee.setVerificationStatus(status.name());
        refereeDao.save(referee, Permission.VERIFY_SP_REFEREE);
    }
}
