package org.pms.silverocean.service.sp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ComplaintCase;
import org.pms.silverocean.database.pms.entities.ProviderProfile;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.database.pms.entities.ServiceBooking;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
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

import java.time.ZonedDateTime;
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

        var booking = bookingDao.findByIdAndCustomerOrProviderForUpdate(request.bookingId(), userId)
                .filter(candidate -> java.util.Objects.equals(candidate.getCreatedBy(), userId))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));

        if (!booking.isActive() || !java.util.Set.of(
                BookingStatus.PAID.name(),
                BookingStatus.IN_PROGRESS.name(),
                BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(),
                BookingStatus.COMPLETED.name()
        ).contains(booking.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_BOOKING_NOT_ACTIVE);
        }
        if (java.util.Set.of(BookingStatus.AWAITING_CUSTOMER_CONFIRMATION.name(),
                BookingStatus.COMPLETED.name()).contains(booking.getStatus())) {
            ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("UTC"));
            if ("CONFIRMED".equals(booking.getSettlementStatus())
                    || booking.getDisputeDeadlineAt() == null
                    || !booking.getDisputeDeadlineAt().isAfter(now)) {
                throw new PMSCustomException(ResponseCode.SP_BOOKING_NOT_ACTIVE,
                        "The customer complaint period for this booking has ended.");
            }
        }

        if (complaintDao.existsComplaintForBooking(request.bookingId())) {
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
        return toDTO(complaint).withoutInternalReviewDetails();
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

    @Transactional(transactionManager = "pmsDBTransactionManager",
            isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void resolveComplaint(long complaintId, ReviewComplaintRequest request) {
        ComplaintCase complaint = complaintDao.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_COMPLAINT_NOT_FOUND));
        if (!java.util.Set.of(ComplaintStatus.UNDER_REVIEW.name(), ComplaintStatus.ESCALATED.name()).contains(complaint.getStatus())) {
            throw new PMSCustomException(ResponseCode.SP_COMPLAINT_CANNOT_REVIEW);
        }
        complaint.setAdminNotes(request.adminNotes());

        ComplaintResolutionAction action = request.action();
        complaint.setPublicResolution(publicResolution(action, request.resolution()));
        if (action == ComplaintResolutionAction.DISMISS) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("DISMISSED");
        } else if (action == ComplaintResolutionAction.WARN) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("WARNED");
        } else if (action == ComplaintResolutionAction.SUSPEND_SERVICE) {
            complaint.setStatus(ComplaintStatus.RESOLVED.name());
            complaint.setResolution("UPHELD");
            requestRefundForUpheldComplaint(complaint);
            providerServiceService.suspendService(complaint.getServiceId(), complaint.getPublicResolution());
            riskScoreService.resetToUnderReview(complaint.getServiceId());
            checkFraudLimitAndBlacklist(complaint);
        } else if (action == ComplaintResolutionAction.ESCALATE) {
            complaint.setStatus(ComplaintStatus.ESCALATED.name());
            complaint.setResolution("ESCALATED");
            complaint.setPublicResolution(null);
        }

        complaintDao.save(complaint, Permission.RESOLVE_SP_COMPLAINT);
        if (ComplaintStatus.RESOLVED.name().equals(complaint.getStatus())) {
            sendComplaintResolvedNotification(complaint);
        }
    }

    public Page<ComplaintCaseDTO> listOpenComplaints(Pageable pageable) {
        return complaintDao.findOpenAndUnderReview(pageable).map(this::toDTO);
    }

    public Page<ComplaintCaseDTO> listComplaintsForService(long serviceId, Pageable pageable) {
        return complaintDao.findByServiceIdAndCreatedByOrServiceOwnerOrSuperAdmin(pageable, serviceId, userDao.getUserId())
                .map(complaint -> toDTO(complaint).withoutInternalReviewDetails());
    }

    private ComplaintCaseDTO toDTO(ComplaintCase complaint) {
        var service = serviceDao.findById(complaint.getServiceId()).orElse(null);
        var booking = bookingDao.findById(complaint.getBookingId()).orElse(null);
        String serviceName = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking == null ? null : booking.getServiceTitleSnapshot(),
                service != null ? org.apache.commons.lang3.StringUtils.defaultIfBlank(
                        service.getTitle(), service.getCategoryName()) : null);
        String currentProviderName = service != null
                ? profileDao.findById(service.getProfileId())
                    .map(ProviderProfile::getBusinessName).orElse(null)
                : null;
        String serviceCreatorName = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking == null ? null : booking.getProviderBusinessNameSnapshot(), currentProviderName);
        String complaintCreatorName = userDao.findById(complaint.getFiledByUserId())
                .map(u -> u.getFullName()).orElse(null);
        return new ComplaintCaseDTO(complaint, serviceName, complaintCreatorName, serviceCreatorName);
    }

    private String publicResolution(ComplaintResolutionAction action, String requestedMessage) {
        String supplied = org.apache.commons.lang3.StringUtils.trimToNull(requestedMessage);
        if (supplied != null) {
            return org.apache.commons.lang3.StringUtils.left(supplied, 2000);
        }
        return switch (action) {
            case DISMISS -> "The complaint was reviewed and was not upheld.";
            case WARN -> "The complaint was reviewed and a formal warning was issued to the provider.";
            case SUSPEND_SERVICE -> "The complaint was upheld and this service was suspended.";
            case ESCALATE -> null;
        };
    }

    private void checkFraudLimitAndBlacklist(ComplaintCase complaint) {
        ProviderService service = serviceDao.findById(complaint.getServiceId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_SERVICE_NOT_FOUND));
        // Serialize the threshold decision for every service owned by this profile.
        // Query execution auto-flushes the current complaint's UPHELD transition.
        ProviderProfile profile = profileDao.findByIdForUpdate(service.getProfileId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_PROFILE_NOT_FOUND));
        int fraudLimit = fraudFailureLimitSupplier.get().intValue();
        int upheldCount = complaintDao.countUpheldComplaintsForProfile(service.getProfileId());
        if (upheldCount >= fraudLimit
                && !org.pms.silverocean.service.sp.enums.ProviderProfileStatus.BLACKLISTED.name().equals(profile.getStatus())) {
            profile.setStatus(org.pms.silverocean.service.sp.enums.ProviderProfileStatus.BLACKLISTED.name());
            profileDao.save(profile, Permission.BLACKLIST_SP);
            log.info("Profile {} blacklisted due to {} upheld complaints", profile.getId(), upheldCount);
        }
    }

    private void requestRefundForUpheldComplaint(ComplaintCase complaint) {
        ServiceBooking booking = bookingDao.findByIdForUpdate(complaint.getBookingId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_BOOKING_NOT_FOUND));
        String refundStatus = org.apache.commons.lang3.StringUtils.defaultIfBlank(
                booking.getRefundStatus(), "NOT_REQUIRED");
        if ("PAID".equals(booking.getPaymentStatus())
                && !java.util.Set.of("REQUESTED", "PROCESSING", "CONFIRMED").contains(refundStatus)) {
            booking.setRefundStatus("REQUESTED");
            booking.setRefundedAmount(booking.getQuotedAmount());
            bookingDao.save(booking, "SP_UPHELD_COMPLAINT_REFUND_REQUESTED");
        }
    }

    private void sendComplaintNotificationToProvider(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service ->
                profileDao.findById(service.getProfileId()).ifPresent(profile ->
                    userDao.findById(profile.getUserId()).ifPresent(user -> {
                        String serviceName=org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(),service.getCategoryName());
                        String safeServiceName=org.springframework.web.util.HtmlUtils.htmlEscape(serviceName);
                        String message = String.format(
                                i18NService.getLocalizedMessage(NotificationType.SP_COMPLAINT_RECEIVED_EMAIL.getBody()),
                                safeServiceName
                        );
                        notificationService.queueEmailAndInApp(user.getEmail(),NotificationType.SP_COMPLAINT_RECEIVED_EMAIL,
                                message,"SERVICE_COMPLAINT_RECEIVED","A customer reported a problem with "+serviceName+". Open /dashboard/marketplace?tab=bookings&bookingId="+complaint.getBookingId()+".");
                    })
                )
            );
        } catch (Exception e) {
            log.warn("Failed to send complaint notification for complaint {}", complaint.getId(), e);
        }
    }

    private void sendComplaintResolvedNotification(ComplaintCase complaint) {
        try {
            serviceDao.findById(complaint.getServiceId()).ifPresent(service -> {
                profileDao.findById(service.getProfileId()).ifPresent(profile ->
                    userDao.findById(profile.getUserId()).ifPresent(user -> {
                        String serviceName=org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(),service.getCategoryName());
                        String safeServiceName=org.springframework.web.util.HtmlUtils.htmlEscape(serviceName);
                        String message = String.format(
                                i18NService.getLocalizedMessage(NotificationType.SP_COMPLAINT_RESOLVED_EMAIL.getBody()),
                                safeServiceName,
                                org.springframework.web.util.HtmlUtils.htmlEscape(
                                        complaint.getPublicResolution() != null ? complaint.getPublicResolution() : "")
                        );
                        notificationService.queueEmailAndInApp(user.getEmail(),NotificationType.SP_COMPLAINT_RESOLVED_EMAIL,
                                message,"SERVICE_COMPLAINT_RESOLVED","The complaint about "+serviceName+" was reviewed. Open /dashboard/marketplace?tab=bookings&bookingId="+complaint.getBookingId()+".");
                    })
                );
                userDao.findById(complaint.getFiledByUserId()).ifPresent(customer -> {
                    String serviceName=org.apache.commons.lang3.StringUtils.defaultIfBlank(service.getTitle(),service.getCategoryName());
                    String safeServiceName=org.springframework.web.util.HtmlUtils.htmlEscape(serviceName);
                    String message = String.format(
                            i18NService.getLocalizedMessage(NotificationType.SP_COMPLAINT_RESOLVED_EMAIL.getBody()),
                            safeServiceName, org.springframework.web.util.HtmlUtils.htmlEscape(
                                    complaint.getPublicResolution() != null ? complaint.getPublicResolution() : ""));
                    notificationService.queueEmailAndInApp(customer.getEmail(), NotificationType.SP_COMPLAINT_RESOLVED_EMAIL,
                            message, "SERVICE_COMPLAINT_RESOLVED", "Your service complaint was reviewed. Open /dashboard/marketplace?tab=bookings&bookingId="+complaint.getBookingId()+" to see the outcome.");
                });
            });
        } catch (Exception e) {
            log.warn("Failed to send complaint resolved notification for complaint {}", complaint.getId(), e);
        }
    }
}
