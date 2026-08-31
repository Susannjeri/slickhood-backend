package org.pms.silverocean.service.visitor;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.database.pms.VisitorAccessEventRepo;
import org.pms.silverocean.database.pms.entities.VisitorAccessEvent;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationChannel;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.enums.AccessDirection;
import org.pms.silverocean.service.visitor.enums.AccessOutcome;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.pms.silverocean.service.visitor.wrappers.CreateVisitorRequest;
import org.pms.silverocean.service.visitor.wrappers.VisitorDTO;
import org.pms.silverocean.service.visitor.wrappers.UpdateVisitorStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class VisitorService {

    private final VisitorDao visitorDao;
    private final UserDao userDao;
    private final NotificationService notificationService;
    private final I18NService i18NService;
    private final ConfigService configService;
    private final VisitorAccessEventRepo accessEventRepo;

    private Supplier<ConfigDTO> visitorNotificationChannel;
    private Supplier<ConfigDTO> visitorNotificationEnabled;

    private static final String VISITOR_EXPIRY_AUDIT_ACTION = "SYSTEM_VISITOR_EXPIRY";

    @PostConstruct
    public void init() {
        visitorNotificationEnabled = configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_ENABLED);
        visitorNotificationChannel = configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_CHANNEL);
    }


    public List<EnumWrapper> getVisitorCategoryList() {
        return EnumSet.allOf(VisitorCategory.class).stream().map(visitorCategory -> new EnumWrapper(visitorCategory.name(), i18NService.getLocalizedMessage(visitorCategory.getName()), null)).toList();
    }


    @Transactional
    public void preRegisterVisitor(CreateVisitorRequest request) {
        Users tenant = userDao.getUserObject();
        if (tenant == null) {
            throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        }

        String localisedPhoneNumber = PMSUtils.getLocalisedPhoneNumber(request.visitorPhoneNumber());
        if (StringUtils.isBlank(localisedPhoneNumber)) {
            throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER);
        }

        visitorDao.findByUnitIdAndVisitorPhoneNumberAndVisitingTime(request.unitId(), localisedPhoneNumber,
                        request.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(ZoneId.of("UTC")))
                .ifPresent(ignored -> {
                    throw new PMSCustomException(ResponseCode.VISITOR_REGISTERED);
                });
        Optional<PropertyIdUnitRefPropertyNameProjection> tenantPropertyId = visitorDao.checkIfTenantInUnit(request.unitId(), tenant.getId());
        if (tenantPropertyId.isEmpty()) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
        Visitor visitor = Visitor.getNewVisitorInstance(request, tenant, localisedPhoneNumber, tenantPropertyId.get());

        visitorDao.save(visitor, Permission.REGISTER_VISITOR);

        if (PMSUtils.booleanizeConfig(visitorNotificationEnabled.get())) {
            sendNotification(VisitorNotificationEvent.VISITOR_BOOKING_CONFIRMATION, tenant, request.visitorName(), request.expectedArrivalTime());
        } else {
            log.info("Visitor Notification is disabled, skipping");
        }
    }

    /** Creates a delivery visit on behalf of the customer when a Soko order is dispatched. */
    @Transactional
    public Visitor preRegisterDeliveryForHost(long hostUserId, CreateVisitorRequest request) {
        Users host = userDao.findById(hostUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION));
        String phone = PMSUtils.getLocalisedPhoneNumber(request.visitorPhoneNumber());
        if (StringUtils.isBlank(phone) || request.visitorCategory() != VisitorCategory.DELIVERY) {
            throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER);
        }
        ZonedDateTime arrival = request.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi"))
                .withZoneSameInstant(ZoneId.of("UTC"));
        visitorDao.findByUnitIdAndVisitorPhoneNumberAndVisitingTime(request.unitId(), phone, arrival)
                .ifPresent(existing -> { throw new PMSCustomException(ResponseCode.VISITOR_REGISTERED); });
        PropertyIdUnitRefPropertyNameProjection property = visitorDao.checkIfResidentInUnit(request.unitId(), hostUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        Visitor visitor = Visitor.getNewVisitorInstance(request, host, phone, property);
        visitor.setPurpose("Soko delivery");
        visitorDao.save(visitor, "SYSTEM_SOKO_DELIVERY_VISITOR");
        return visitor;
    }

    public List<VisitorDTO> listMyVisitors(Pageable pageable, Optional<String> phoneNumber) {
        long loggedInUserId = userDao.getUserId();
        Pageable bounded = bounded(pageable);
        List<Visitor> visitors;
        if (phoneNumber.filter(StringUtils::isNotBlank).isPresent()) {
            String normalizedPhone = PMSUtils.getLocalisedPhoneNumber(phoneNumber.get());
            if (StringUtils.isBlank(normalizedPhone)) return List.of();
            visitors = visitorDao.findByTenantOrGuardOrLandlordOrPropertyManagerAndPhoneNumber(bounded, loggedInUserId, normalizedPhone);
        } else {
            visitors = visitorDao.findByTenantOrGuardOrLandlordOrPropertyManager(bounded, loggedInUserId);
        }
        return visitors.stream().map(VisitorDTO::new).toList();
    }

    public Page<VisitorDTO> listVisitorsByUnit(Pageable pageable, long unitId, Optional<VisitorStatus> status) {
        if (!visitorDao.canStaffOrOwnerAccessUnit(unitId, userDao.getUserId())) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
        Pageable bounded = bounded(pageable);
        Page<Visitor> visitors = status
                .map(s -> visitorDao.findByUnitIdAndStatus(bounded, unitId, s.name()))
                .orElseGet(() -> visitorDao.findByUnitId(bounded, unitId));
        return visitors.map(VisitorDTO::new);
    }

    @Transactional
    public void updateVisitorStatus(long visitorId, VisitorStatus newStatus) {
        updateVisitorStatus(visitorId, new UpdateVisitorStatusRequest(newStatus, null));
    }

    @Transactional
    public void updateVisitorStatus(long visitorId, UpdateVisitorStatusRequest request) {
        Users loggedInUser = userDao.getUserObject();
        if (loggedInUser == null) throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        Visitor visitor = visitorDao.findByIdAndGuard(visitorId, loggedInUser.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.VISITOR_NOT_FOUND));

        VisitorStatus newStatus = request.status();
        VisitorStatus currentStatus = VisitorStatus.valueOf(visitor.getStatus());
        if (currentStatus == VisitorStatus.CANCELLED || currentStatus == VisitorStatus.EXPIRED || currentStatus == VisitorStatus.DENIED || currentStatus == VisitorStatus.DELETED) {
            throw new PMSCustomException(ResponseCode.VISITOR_ALREADY_PROCESSED);
        }
        boolean entry = newStatus == VisitorStatus.CHECKED_IN && Set.of(
                VisitorStatus.PENDING, VisitorStatus.APPROVED, VisitorStatus.ARRIVED, VisitorStatus.CHECKED_OUT).contains(currentStatus);
        boolean exit = newStatus == VisitorStatus.CHECKED_OUT && currentStatus == VisitorStatus.CHECKED_IN;
        if (!entry && !exit) {
            throw new PMSCustomException(ResponseCode.VISITOR_INVALID_STATUS);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        if (VisitorStatus.CHECKED_IN == newStatus) {
            if (visitor.getExpectedArrivalTime() == null) throw new PMSCustomException(ResponseCode.VISITOR_INVALID_STATUS);
            ZonedDateTime validFrom = visitor.getValidFrom() == null ? visitor.getExpectedArrivalTime().minusHours(2) : visitor.getValidFrom();
            ZonedDateTime validUntil = visitor.getValidUntil() == null ? visitor.getExpectedArrivalTime().plusHours(8) : visitor.getValidUntil();
            if (now.isBefore(validFrom) || now.isAfter(validUntil) || visitor.getEntryCount() >= Math.max(1, visitor.getMaxEntries())) {
                throw new PMSCustomException(ResponseCode.VISITOR_INVALID_STATUS);
            }
            if (VisitType.DRIVE_IN.name().equals(visitor.getVisitType()) &&
                    !normalizePlate(visitor.getVehiclePlate()).equals(normalizePlate(request.vehiclePlate()))) {
                throw new PMSCustomException(ResponseCode.VISITOR_INVALID_STATUS);
            }
            visitor.setCheckInGuardName(loggedInUser.getFullName());
            visitor.setCheckedInAt(now);
            visitor.setEntryCount(visitor.getEntryCount() + 1);
        } else if (VisitorStatus.CHECKED_OUT == newStatus) {
            visitor.setCheckOutGuardName(loggedInUser.getFullName());
            visitor.setCheckedOutAt(now);
        }
        visitor.setStatus(newStatus.name());

        visitorDao.save(visitor, Permission.UPDATE_VISITOR_STATUS);
        recordStaffedGateEvent(visitor, loggedInUser.getId(), newStatus, request.vehiclePlate(), now);
        notifyHostIfEnabled(visitor, newStatus, now);
    }

    @Transactional
    public void cancelVisitor(long visitorId) {
        long createdBy = userDao.getUserId();
        Visitor visitor = visitorDao.findByIdAndCreatedBy(visitorId, createdBy)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.VISITOR_NOT_FOUND));

        VisitorStatus currentStatus = VisitorStatus.valueOf(visitor.getStatus());
        if (!Set.of(VisitorStatus.PENDING, VisitorStatus.PENDING_APPROVAL, VisitorStatus.APPROVED, VisitorStatus.ARRIVED).contains(currentStatus)) {
            throw new PMSCustomException(ResponseCode.VISITOR_ALREADY_PROCESSED);
        }

        visitor.setStatus(VisitorStatus.CANCELLED.name());
        visitorDao.save(visitor, Permission.CANCEL_VISITOR);
    }

    @Transactional
    public void deleteVisitor(long visitorId) {
        long createdBy = userDao.getUserId();
        Visitor visitor = visitorDao.findByIdAndCreatedBy(visitorId, createdBy)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.VISITOR_NOT_FOUND));

        if (VisitorStatus.CHECKED_IN.name().equals(visitor.getStatus())) {
            throw new PMSCustomException(ResponseCode.VISITOR_ALREADY_PROCESSED);
        }
        visitor.setStatus(VisitorStatus.DELETED.name());
        visitor.setActive(false);
        visitor.setCredentialHash(null);
        visitor.setCredentialHint(null);
        visitor.setVisitorName("Deleted visitor");
        visitor.setPhoneNumber(null);
        visitor.setVehiclePlate(null);
        visitor.setPurpose(null);
        visitor.setCompanyName(null);
        visitor.setTrackingNumber(null);
        visitorDao.save(visitor, Permission.DELETE_VISITOR);
    }

    public void sendNotification(VisitorNotificationEvent event,
                                 Users recipient,
                                 Object... templateArgs) {
        Set<NotificationChannel> targets = getEnabledNotificationChannels();
        for (NotificationChannel channel : targets) {
            NotificationType type = event.typeFor(channel);
            String destination = resolveDestination(recipient, channel);

            if (StringUtils.isBlank(destination)) {
                log.warn("Skipping {} {} — recipient {} has no {} address",
                        channel, event, recipient.getId(), channel);
                continue;
            }

            String message = String.format(
                    i18NService.getLocalizedMessage(type.getBody()),
                    templateArgs
            );

            notificationService.sendNotification(new NotificationDTO(message, destination, type));
        }
    }

    private Set<NotificationChannel> getEnabledNotificationChannels() {
        String configuredChannel = visitorNotificationChannel.get().stringValue();
        if (StringUtils.isNotBlank(configuredChannel)) {
            if (configuredChannel.equalsIgnoreCase("Email")) {
                return Set.of(NotificationChannel.EMAIL);
            } else if (configuredChannel.equalsIgnoreCase("sms")) {
                return Set.of(NotificationChannel.SMS);
            } else if (configuredChannel.equalsIgnoreCase("all")) {
                return Set.of(NotificationChannel.SMS, NotificationChannel.EMAIL);
            }
            return Set.of(NotificationChannel.EMAIL);
        } else {
            return Set.of(NotificationChannel.EMAIL);
        }
    }

    private String resolveDestination(Users recipient, NotificationChannel channel) {
        return switch (channel) {
            case SMS -> recipient.getPhoneNumber();
            case EMAIL -> recipient.getEmail();
        };
    }

    @Transactional
    boolean cleanUpPendingExpiredVisitorRecords(int batchSize, int expiryDays) {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(expiryDays);
        List<Visitor> expiredVisitors = visitorDao.findExpiredPendingVisitors(cutoff, batchSize);
        if (expiredVisitors.isEmpty()) {
            log.info("No pending expired visitors found");
            return false;
        }
        log.info("Expiring {} pending visitor records", expiredVisitors.size());
        expiredVisitors.forEach(visitor -> {
            visitor.setStatus(VisitorStatus.EXPIRED.name());
            visitorDao.save(visitor, VISITOR_EXPIRY_AUDIT_ACTION);
        });
        return expiredVisitors.size() == batchSize;
    }

    private Pageable bounded(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100), pageable.getSort());
    }

    private String normalizePlate(String plate) {
        return StringUtils.defaultString(plate).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private void recordStaffedGateEvent(Visitor visitor, long guardId, VisitorStatus status, String vehiclePlate, ZonedDateTime now) {
        VisitorAccessEvent event = new VisitorAccessEvent();
        event.setVisitorId(visitor.getId());
        event.setPropertyId(visitor.getPropertyId());
        event.setSource("STAFFED_GATE");
        event.setDirection(status == VisitorStatus.CHECKED_IN ? AccessDirection.ENTRY.name() : AccessDirection.EXIT.name());
        event.setOutcome(AccessOutcome.GRANTED.name());
        event.setReasonCode("ACCESS_GRANTED");
        event.setCorrelationId("staff-" + UUID.randomUUID());
        event.setVehiclePlate(normalizePlate(vehiclePlate));
        event.setOccurredAt(now);
        event.setCreatedBy(guardId);
        event.setActive(true);
        accessEventRepo.save(event);
    }

    private void notifyHostIfEnabled(Visitor visitor, VisitorStatus status, ZonedDateTime occurredAt) {
        if (visitorNotificationEnabled == null || !PMSUtils.booleanizeConfig(visitorNotificationEnabled.get()) || visitor.getHostUserId() == null) return;
        userDao.findById(visitor.getHostUserId()).ifPresent(host -> sendNotification(
                status == VisitorStatus.CHECKED_IN ? VisitorNotificationEvent.VISITOR_ARRIVAL : VisitorNotificationEvent.VISITOR_DEPARTURE,
                host, visitor.getVisitorName(), occurredAt));
    }


}
