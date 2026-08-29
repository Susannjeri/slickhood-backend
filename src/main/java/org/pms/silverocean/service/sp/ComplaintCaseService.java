package org.pms.silverocean.service.sp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.sp.dao.ComplaintCaseDao;
import org.pms.silverocean.service.sp.dao.ProviderProfileDao;
import org.pms.silverocean.service.sp.dao.ServiceBookingDao;
import org.pms.silverocean.service.sp.enums.BookingStatus;
import org.pms.silverocean.service.sp.enums.ComplaintResolutionAction;
import org.pms.silverocean.service.sp.enums.ComplaintStatus;
import org.pms.silverocean.service.sp.wrappers.ComplaintCaseDTO;
import org.pms.silverocean.service.sp.wrappers.FileComplaintRequest;
import org.pms.silverocean.service.sp.wrappers.ReviewComplaintRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplaintCaseService {
    private final ComplaintCaseDao complaintDao;
    private final ProviderServiceDao serviceDao;
    private final ProviderProfileDao profileDao;
    private final ServiceBookingDao bookingDao;
    private final ProviderServiceService providerServiceService;
    private final RiskScoreService riskScoreService;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final ConfigService configService;

    private Supplier<ConfigDTO> fraudFailureLimitSupplier;

    @PostConstruct
    public void init() {
        fraudFailureLimitSupplier = configService.getConfigByName(PMSConfigs.SP_FRAUD_FAILURE_LIMIT_VALUE);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ComplaintCaseDTO fileComplaint(FileComplaintRequest request) {
        long userId = userDao.getUserId();

        var booking = bookingDao.findByIdAndBookingCreatedByForRead(request.bookingId(), userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));

        if (!booking.isActive() || BookingStatus.CANCELLED.name().equals(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_NOT_ACTIVE);
        }

        if (complaintDao.existsOpenComplaintForBooking(request.bookingId())) {
            throw new PMSCustomException(ResponseCode.SP_COMPLAINT_ALREADY_EXISTS);
        }

        ComplaintCase complaint = new ComplaintCase();
        complaint.setServiceId(booking.getServiceId());
        complaint.setBookingId(request.bookingId());
        complaint.setFiledByUserId(userId);
        complaint.setDescription(request.description());
        complaint.setStatus(ComplaintStatus.OPEN.name());
        complaint.setActive(true);
        complaint.setCreatedBy(userId);
        complaintDao.save(complaint, Permission.FILE_SP_COMPLAINT);

        sendComplaintNotificationToProvider(complaint);
        return toDTO(complaint);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void assignReview(long complaintId) {
        long adminId = userDao.getUserId();
        ComplaintCase complaint = complaintDao.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_COMPLAINT_NOT_FOUND));
        if (!ComplaintStatus.OPEN.name().equals(complaint.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_COMPLAINT_CANNOT_REVIEW);
        }
        complaint.setStatus(ComplaintStatus.UNDER_REVIEW.name());
        complaint.setAssignedAdminId(adminId);
        complaintDao.save(complaint, Permission.REVIEW_SP_COMPLAINT);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void resolveComplaint(long complaintId, ReviewComplaintRequest request) {
        ComplaintCase complaint = complaintDao.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_COMPLAINT_NOT_FOUND));
        complaint.setAdminNotes(request.adminNotes());

        ComplaintResolutionAction action = request.action();
        if (action == ComplaintResolutionAction.DISMISS) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("DISMISSED");
        } else if (action == ComplaintResolutionAction.WARN) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("WARNED");
            addAdminNoteToProfile(complaint);
        } else if (action == ComplaintResolutionAction.SUSPEND_SERVICE) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("UPHELD");
            providerServiceService.suspendService(complaint.getServiceId(), request.adminNotes());
            riskScoreService.resetToUnderReview(complaint.getServiceId());
            checkFraudLimitAndBlacklist(complaint);
        } else if (action == ComplaintResolutionAction.ESCALATE) {
            complaint.setStatus(ComplaintStatus.ESCALATED.name());
        }

        complaintDao.save(complaint, Permission.RESOLVE_SP_COMPLAINT);
        sendComplaintResolvedNotification(complaint);
    }

    public Page<ComplaintCaseDTO> listOpenComplaints(Pageable pageable) {
        return complaintDao.findOpenAndUnderReview(pageable).map(this::toDTO);
    }

    public Page<ComplaintCaseDTO> listComplaintsForService(long serviceId, Pageable pageable) {
        return complaintDao.findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(pageable, serviceId, userDao.getUserId()).map(this::toDTO);
    }

    private ComplaintCaseDTO toDTO(ComplaintCase complaint) {
        var service = serviceDao.findById(complaint.getServiceId()).orElse(null);
        String serviceName = service != null ? service.getCategoryName() : null;
        String serviceCreatorName = service != null
                ? userDao.findById(service.getCreatedBy()).map(u -> u.getFullName()).orElse(null)
                : null;
        String complaintCreatorName = userDao.findById(complaint.getFiledByUserId())
                .map(u -> u.getFullName()).orElse(null);
        return new ComplaintCaseDTO(complaint, serviceName, complaintCreatorName, serviceCreatorName);
    }

    private void addAdminNoteToProfile(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service -> {
                profileDao.findById(service.getProfileId()).ifPresent(profile -> {
                    String existing = profile.getAdminNotes() != null ? profile.getAdminNotes() : "";
                    profile.setAdminNotes(existing + " | Warning: " + complaint.getAdminNotes());
                    profileDao.save(profile, Permission.RESOLVE_SP_COMPLAINT);
                });
            });
        } catch (Exception e) {
            log.warn("Failed to add admin note to profile for complaint {}", complaint.getId(), e);
        }
    }

    private void checkFraudLimitAndBlacklist(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service -> {
                int fraudLimit = fraudFailureLimitSupplier.get().intValue();
                int upheldCount = complaintDao.countUpheldComplaintsForProfile(service.getProfileId());
                if (upheldCount >= fraudLimit) {
                    profileDao.findById(service.getProfileId()).ifPresent(profile -> {
                        org.pms.silverocean.service.sp.enums.ProviderProfileStatus blacklisted = org.pms.silverocean.service.sp.enums.ProviderProfileStatus.BLACKLISTED;
                        profile.setStatus(blacklisted.name());
                        profileDao.save(profile, Permission.BLACKLIST_SP);
                        log.info("Profile {} blacklisted due to {} upheld complaints", profile.getId(), upheldCount);
                    });
                }
            });
        } catch (Exception e) {
            log.warn("Error during fraud limit check for complaint {}", complaint.getId(), e);
        }
    }

    private void sendComplaintNotificationToProvider(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service ->
                profileDao.findById(service.getProfileId()).ifPresent(profile ->
                    userDao.findById(profile.getUserId()).ifPresent(user -> {
                        String message = String.format(
                                i18NService.getLocalizedMessage(NotificationType.SP_COMPLAINT_RECEIVED_EMAIL.getBody()),
                                service.getCategoryName()
                        );
                        notificationService.sendNotification(new NotificationDTO(message, user.getEmail(), NotificationType.SP_COMPLAINT_RECEIVED_EMAIL));
                    })
                )
            );
        } catch (Exception e) {
            log.warn("Failed to send complaint notification for complaint {}", complaint.getId(), e);
        }
    }

    private void sendComplaintResolvedNotification(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service ->
                profileDao.findById(service.getProfileId()).ifPresent(profile ->
                    userDao.findById(profile.getUserId()).ifPresent(user -> {
                        String message = String.format(
                                i18NService.getLocalizedMessage(NotificationType.SP_COMPLAINT_RESOLVED_EMAIL.getBody()),
                                service.getCategoryName(),
                                complaint.getResolution() != null ? complaint.getResolution() : ""
                        );
                        notificationService.sendNotification(new NotificationDTO(message, user.getEmail(), NotificationType.SP_COMPLAINT_RESOLVED_EMAIL));
                    })
                )
            );
        } catch (Exception e) {
            log.warn("Failed to send complaint resolved notification for complaint {}", complaint.getId(), e);
        }
    }
}
