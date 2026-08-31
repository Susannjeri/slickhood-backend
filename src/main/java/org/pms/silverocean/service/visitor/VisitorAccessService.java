package org.pms.silverocean.service.visitor;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.GateDeviceRepo;
import org.pms.silverocean.database.pms.GateRequestNonceRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.VisitorAccessEventRepo;
import org.pms.silverocean.database.pms.VisitorRepo;
import org.pms.silverocean.database.pms.entities.GateDevice;
import org.pms.silverocean.database.pms.entities.GateRequestNonce;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.database.pms.entities.VisitorAccessEvent;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.config.ConfigDTO;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.visitor.enums.AccessDirection;
import org.pms.silverocean.service.visitor.enums.AccessOutcome;
import org.pms.silverocean.service.visitor.enums.VisitType;
import org.pms.silverocean.service.visitor.enums.VisitorCategory;
import org.pms.silverocean.service.visitor.enums.VisitorStatus;
import org.pms.silverocean.service.visitor.wrappers.AccessDecisionDTO;
import org.pms.silverocean.service.visitor.wrappers.GateDeviceDTO;
import org.pms.silverocean.service.visitor.wrappers.GateDeviceRegistrationRequest;
import org.pms.silverocean.service.visitor.wrappers.RegisterVisitRequest;
import org.pms.silverocean.service.visitor.wrappers.RegisteredVisitDTO;
import org.pms.silverocean.service.visitor.wrappers.SmartGateDecisionRequest;
import org.pms.silverocean.service.visitor.wrappers.VisitorDTO;
import org.pms.silverocean.service.visitor.wrappers.VisitorDecisionRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class VisitorAccessService {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Duration SIGNATURE_WINDOW = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VisitorRepo visitorRepo;
    private final VisitorDao visitorDao;
    private final UserDao userDao;
    private final UnitRepo unitRepo;
    private final PropertyRepo propertyRepo;
    private final GateDeviceRepo gateDeviceRepo;
    private final GateRequestNonceRepo nonceRepo;
    private final VisitorAccessEventRepo eventRepo;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private Supplier<ConfigDTO> visitorNotificationEnabled;

    @PostConstruct
    public void init() {
        visitorNotificationEnabled = configService.getConfigByName(PMSConfigs.VISITOR_NOTIFICATION_ENABLED);
    }

    @Transactional
    public RegisteredVisitDTO registerExpected(RegisterVisitRequest request) {
        Users host = requireUser();
        DbUnitDTO unit = unitRepo.findByIdAndStaffOrOwnerOrTenant(request.unitId(), host.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        return createVisit(request, host.getId(), host.getId(), unit, false);
    }

    @Transactional
    public VisitorDTO registerUnplanned(RegisterVisitRequest request) {
        Users guard = requireUser();
        DbUnitDTO unit = unitRepo.findByIdAndStaffOrOwnerOrTenant(request.unitId(), guard.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        if (request.hostUserId() == null || (unitRepo.findByIdAndStaffOrOwnerOrTenant(request.unitId(), request.hostUserId()).isEmpty()
                && unitRepo.findDTOByIdAndHomeowner(request.unitId(), request.hostUserId()).isEmpty())) {
            throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        }
        RegisteredVisitDTO registered = createVisit(request, guard.getId(), request.hostUserId(), unit, true);
        userDao.findById(request.hostUserId()).ifPresent(host -> queueSms(host.getPhoneNumber(), NotificationType.VISITOR_APPROVAL_REQUEST_SMS,
                request.visitorName().trim() + " is waiting for your approval to visit " + unit.ref() + ". Open SlickHood Visitor Management to approve or deny access."));
        return registered.visit();
    }

    @Transactional
    public RegisteredVisitDTO decide(long visitorId, VisitorDecisionRequest request) {
        long hostId = userDao.getUserId();
        Visitor visitor = visitorRepo.findByIdAndHostUserId(visitorId, hostId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.VISITOR_NOT_FOUND));
        if (VisitorStatus.valueOf(visitor.getStatus()) != VisitorStatus.PENDING_APPROVAL) {
            throw new PMSCustomException(ResponseCode.VISITOR_ALREADY_PROCESSED);
        }
        visitor.setApprovedBy(hostId);
        visitor.setApprovedAt(ZonedDateTime.now(UTC));
        if (request.decision() == VisitorDecisionRequest.Decision.DENY) {
            if (StringUtils.isBlank(request.reason())) throw new PMSCustomException(ResponseCode.VISITOR_INVALID_STATUS);
            visitor.setDecisionReason(request.reason().trim());
            visitor.setStatus(VisitorStatus.DENIED.name());
            visitorDao.save(visitor, "DENY_VISITOR_ACCESS");
            queueSms(visitor.getPhoneNumber(), NotificationType.VISITOR_ACCESS_DENIED_SMS,
                    "Your visit to " + visitor.getPropertyName() + " was not approved. Contact your host if you need assistance.");
            return new RegisteredVisitDTO(new VisitorDTO(visitor), null);
        }
        String accessCode = issueCredential(visitor);
        visitor.setDecisionReason(null);
        visitor.setStatus(VisitorStatus.APPROVED.name());
        visitorDao.save(visitor, "APPROVE_VISITOR_ACCESS");
        queueSms(visitor.getPhoneNumber(), NotificationType.VISITOR_ACCESS_APPROVED_SMS,
                "Your visit to " + visitor.getPropertyName() + " is approved. Access code: " + accessCode +
                        ". Valid until " + visitor.getValidUntil() + ". Keep this code private.");
        return new RegisteredVisitDTO(new VisitorDTO(visitor), accessCode);
    }

    @Transactional
    public GateDeviceDTO registerDevice(GateDeviceRegistrationRequest request) {
        long userId = userDao.getUserId();
        propertyRepo.findByIdAndStaffOrOwner(request.propertyId(), userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        parsePublicKey(request.ed25519PublicKey());
        GateDevice device = new GateDevice();
        device.setDeviceCode("gate_" + UUID.randomUUID().toString().replace("-", ""));
        device.setPropertyId(request.propertyId());
        device.setDisplayName(request.displayName().trim());
        device.setGateName(StringUtils.trimToNull(request.gateName()));
        device.setLaneName(StringUtils.trimToNull(request.laneName()));
        device.setPublicKey(request.ed25519PublicKey().trim());
        device.setEnabled(true);
        device.setActive(true);
        device.setCreatedBy(userId);
        return new GateDeviceDTO(gateDeviceRepo.save(device));
    }

    public List<GateDeviceDTO> listDevices(long propertyId) {
        propertyRepo.findByIdAndStaffOrOwner(propertyId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        return gateDeviceRepo.findAllByPropertyIdAndActiveTrueOrderByDisplayName(propertyId).stream()
                .map(GateDeviceDTO::new).toList();
    }

    public List<org.pms.silverocean.service.visitor.projections.GuardHostOptionProjection> guardHostOptions() {
        long guardId = userDao.getUserId();
        java.util.Map<String, org.pms.silverocean.service.visitor.projections.GuardHostOptionProjection> options = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.concat(unitRepo.findGuardHostOptions(guardId).stream(),
                        unitRepo.findGuardHomeownerOptions(guardId).stream())
                .forEach(option -> options.put(option.getUnitId() + ":" + option.getHostUserId(), option));
        return List.copyOf(options.values());
    }

    @Transactional
    public GateDeviceDTO setDeviceEnabled(String deviceCode, boolean enabled) {
        GateDevice device = gateDeviceRepo.findByDeviceCodeAndActiveTrue(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_DEVICE"));
        propertyRepo.findByIdAndStaffOrOwner(device.getPropertyId(), userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        device.setEnabled(enabled);
        return new GateDeviceDTO(gateDeviceRepo.save(device));
    }

    public Page<org.pms.silverocean.service.visitor.wrappers.AccessEventDTO> listEvents(long propertyId, Pageable pageable) {
        propertyRepo.findByIdAndStaffOrOwner(propertyId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        return eventRepo.findAllByPropertyIdOrderByOccurredAtDesc(propertyId, pageable)
                .map(org.pms.silverocean.service.visitor.wrappers.AccessEventDTO::new);
    }

    @Transactional
    public AccessDecisionDTO decideFromDevice(String deviceCode, long timestamp, String nonce,
                                              String signature, String rawBody) {
        if (StringUtils.isBlank(deviceCode) || deviceCode.length() > 64 || StringUtils.isBlank(signature) || signature.length() > 256
                || rawBody == null || rawBody.length() > 4096) throw new IllegalArgumentException("INVALID_REQUEST");
        ZonedDateTime now = ZonedDateTime.now(UTC);
        GateDevice device = gateDeviceRepo.findByDeviceCodeAndEnabledTrueAndActiveTrue(deviceCode)
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_DEVICE"));
        verifyFreshRequest(timestamp, nonce, now);
        verifySignature(device, timestamp, nonce, signature, rawBody);
        rememberNonce(device, nonce, now);
        device.setLastSeenAt(now);
        gateDeviceRepo.save(device);

        SmartGateDecisionRequest request = parseRequest(rawBody);
        validateRequest(request);
        return eventRepo.findByCorrelationId(request.correlationId())
                .map(event -> {
                    if (event.getDeviceId() == null || event.getDeviceId() != device.getId() || event.getPropertyId() != device.getPropertyId()) {
                        throw new IllegalArgumentException("CORRELATION_CONFLICT");
                    }
                    return new AccessDecisionDTO(AccessOutcome.GRANTED.name().equals(event.getOutcome()), event.getReasonCode(),
                            event.getCorrelationId(), event.getVisitorId(), null, null, null, event.getOccurredAt());
                })
                .orElseGet(() -> evaluate(device, request, now));
    }

    private RegisteredVisitDTO createVisit(RegisterVisitRequest request, long createdBy, long hostUserId,
                                           DbUnitDTO unit, boolean requiresApproval) {
        String phone = PMSUtils.getLocalisedPhoneNumber(request.visitorPhoneNumber());
        if (StringUtils.isBlank(phone)) throw new PMSCustomException(ResponseCode.INVALID_PHONENUMBER);
        Property property = propertyRepo.findById(unit.propertyId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        ZonedDateTime expected = request.expectedArrivalTime().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(UTC);
        ZonedDateTime validFrom = expected.minusHours(2);
        ZonedDateTime validUntil = request.validUntil() == null ? expected.plusHours(8)
                : request.validUntil().atZone(ZoneId.of("Africa/Nairobi")).withZoneSameInstant(UTC);
        if (!validUntil.isAfter(validFrom) || validUntil.isAfter(expected.plusDays(30))) {
            throw new IllegalArgumentException("Invalid access validity window");
        }
        if (request.visitType() == VisitType.DRIVE_IN && StringUtils.isBlank(request.vehiclePlate())) {
            throw new IllegalArgumentException("Vehicle plate is required for drive-in visits");
        }
        Visitor visitor = new Visitor();
        visitor.setUnitId(unit.unitId()); visitor.setUnitRef(unit.ref());
        visitor.setPropertyId(unit.propertyId()); visitor.setPropertyName(property.getName());
        visitor.setVisitorName(request.visitorName().trim()); visitor.setPhoneNumber(phone);
        visitor.setVehiclePlate(normalizePlate(request.vehiclePlate())); visitor.setParkingLot(StringUtils.trimToNull(request.parkingLot()));
        visitor.setExpectedArrivalTime(expected); visitor.setValidFrom(validFrom); visitor.setValidUntil(validUntil);
        visitor.setVisitType(request.visitType().name());
        visitor.setCategory((request.visitorCategory() == null
                ? (request.visitType() == VisitType.DELIVERY ? VisitorCategory.DELIVERY : VisitorCategory.GUEST)
                : request.visitorCategory()).name());
        visitor.setPurpose(StringUtils.trimToNull(request.purpose()));
        visitor.setCompanyName(StringUtils.trimToNull(request.companyName()));
        visitor.setTrackingNumber(StringUtils.trimToNull(request.trackingNumber()));
        visitor.setChargeable(request.chargeable()); visitor.setCreatedBy(createdBy); visitor.setHostUserId(hostUserId);
        visitor.setMaxEntries(request.maxEntries() == null ? 1 : Math.min(request.maxEntries(), 20));
        visitor.setRequiresApproval(requiresApproval); visitor.setActive(true);
        String code = null;
        if (requiresApproval) {
            visitor.setStatus(VisitorStatus.PENDING_APPROVAL.name());
        } else {
            visitor.setStatus(VisitorStatus.APPROVED.name());
            visitor.setApprovedBy(hostUserId); visitor.setApprovedAt(ZonedDateTime.now(UTC));
            code = issueCredential(visitor);
        }
        visitorDao.save(visitor, requiresApproval ? "REGISTER_UNPLANNED_VISITOR" : "REGISTER_ACCESS_VISITOR");
        if (!requiresApproval) {
            queueSms(visitor.getPhoneNumber(), NotificationType.VISITOR_ACCESS_APPROVED_SMS,
                    "Your visit to " + visitor.getPropertyName() + " is approved. Access code: " + code +
                            ". Valid until " + visitor.getValidUntil() + ". Keep this code private.");
        }
        return new RegisteredVisitDTO(new VisitorDTO(visitor), code);
    }

    private AccessDecisionDTO evaluate(GateDevice device, SmartGateDecisionRequest request, ZonedDateTime now) {
        Visitor visitor = visitorRepo.findByCredentialHashForUpdate(sha256(request.accessCode()))
                .orElse(null);
        String reason = validateAccess(device, visitor, request, now);
        boolean granted = "ACCESS_GRANTED".equals(reason);
        if (granted) {
            if (request.direction() == AccessDirection.ENTRY) {
                visitor.setStatus(VisitorStatus.CHECKED_IN.name());
                visitor.setCheckedInAt(now);
                visitor.setEntryCount(visitor.getEntryCount() + 1);
                visitor.setCheckInGuardName(device.getDisplayName());
            } else {
                visitor.setStatus(VisitorStatus.CHECKED_OUT.name());
                visitor.setCheckedOutAt(now);
                visitor.setCheckOutGuardName(device.getDisplayName());
            }
            visitorDao.save(visitor, "SMART_GATE_" + request.direction().name());
            notifyHost(visitor, request.direction(), now);
        }
        VisitorAccessEvent event = new VisitorAccessEvent();
        event.setVisitorId(visitor == null ? null : visitor.getId()); event.setPropertyId(device.getPropertyId());
        event.setDeviceId(device.getId()); event.setSource("SMART_GATE"); event.setDirection(request.direction().name());
        event.setOutcome(granted ? AccessOutcome.GRANTED.name() : AccessOutcome.DENIED.name()); event.setReasonCode(reason);
        event.setCorrelationId(request.correlationId()); event.setVehiclePlate(normalizePlate(request.vehiclePlate()));
        event.setOccurredAt(now); event.setActive(true); eventRepo.save(event);
        return new AccessDecisionDTO(granted, reason, request.correlationId(), visitor == null ? null : visitor.getId(),
                visitor == null ? null : visitor.getVisitorName(), visitor == null ? null : visitor.getUnitRef(),
                visitor == null ? null : visitor.getVisitType(), now);
    }

    private String validateAccess(GateDevice device, Visitor visitor, SmartGateDecisionRequest request, ZonedDateTime now) {
        if (visitor == null) return "INVALID_CREDENTIAL";
        if (visitor.getPropertyId() != device.getPropertyId()) return "WRONG_PROPERTY";
        if (visitor.getValidFrom() == null || now.isBefore(visitor.getValidFrom())) return "NOT_YET_VALID";
        if (visitor.getValidUntil() == null || now.isAfter(visitor.getValidUntil())) return "CREDENTIAL_EXPIRED";
        VisitorStatus status = VisitorStatus.valueOf(visitor.getStatus());
        if (request.direction() == AccessDirection.ENTRY) {
            if (status == VisitorStatus.CHECKED_IN) return "ANTI_PASSBACK";
            if (!(status == VisitorStatus.APPROVED || status == VisitorStatus.CHECKED_OUT)) return "VISIT_NOT_APPROVED";
            if (visitor.getEntryCount() >= visitor.getMaxEntries()) return "ENTRY_LIMIT_REACHED";
            if (VisitType.DRIVE_IN.name().equals(visitor.getVisitType()) &&
                    !normalizePlate(visitor.getVehiclePlate()).equals(normalizePlate(request.vehiclePlate()))) return "VEHICLE_MISMATCH";
        } else if (status != VisitorStatus.CHECKED_IN) return "NOT_INSIDE";
        return "ACCESS_GRANTED";
    }

    private void verifyFreshRequest(long timestamp, String nonce, ZonedDateTime now) {
        if (StringUtils.length(nonce) < 16 || StringUtils.length(nonce) > 100) throw new IllegalArgumentException("INVALID_NONCE");
        ZonedDateTime sentAt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp), UTC);
        if (Duration.between(sentAt, now).abs().compareTo(SIGNATURE_WINDOW) > 0) throw new IllegalArgumentException("STALE_REQUEST");
    }

    private void verifySignature(GateDevice device, long timestamp, String nonce, String encodedSignature, String rawBody) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(parsePublicKey(device.getPublicKey()));
            verifier.update((timestamp + "\n" + nonce + "\n" + rawBody).getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(encodedSignature))) throw new IllegalArgumentException("INVALID_SIGNATURE");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("INVALID_SIGNATURE", e); }
    }

    private void rememberNonce(GateDevice device, String nonce, ZonedDateTime now) {
        GateRequestNonce entity = new GateRequestNonce(); entity.setDeviceId(device.getId()); entity.setNonce(nonce);
        entity.setExpiresAt(now.plusMinutes(10));
        try { nonceRepo.saveAndFlush(entity); }
        catch (DataIntegrityViolationException e) { throw new IllegalArgumentException("REPLAY_DETECTED"); }
    }

    private PublicKey parsePublicKey(String encoded) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(key));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid Ed25519 public key", e); }
    }

    private SmartGateDecisionRequest parseRequest(String body) {
        try { return objectMapper.readValue(body, SmartGateDecisionRequest.class); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("INVALID_REQUEST", e); }
    }

    private void validateRequest(SmartGateDecisionRequest request) {
        if (request == null || request.direction() == null || request.accessCode() == null || request.accessCode().length() != 43
                || request.correlationId() == null || !request.correlationId().matches("[A-Za-z0-9._:-]{1,64}")
                || (request.vehiclePlate() != null && request.vehiclePlate().length() > 20)) {
            throw new IllegalArgumentException("INVALID_REQUEST");
        }
    }

    private void notifyHost(Visitor visitor, AccessDirection direction, ZonedDateTime now) {
        if (visitor.getHostUserId() == null) return;
        userDao.findById(visitor.getHostUserId()).ifPresent(host -> queueSms(host.getPhoneNumber(),
                direction == AccessDirection.ENTRY ? NotificationType.VISITOR_ARRIVAL_SMS : NotificationType.VISITOR_DEPARTURE_SMS,
                "Your visitor " + visitor.getVisitorName() + (direction == AccessDirection.ENTRY ? " arrived at " : " departed at ") + now + "."));
    }

    private void queueSms(String phoneNumber, NotificationType type, String message) {
        if (visitorNotificationEnabled != null && PMSUtils.booleanizeConfig(visitorNotificationEnabled.get()) && StringUtils.isNotBlank(phoneNumber)) {
            notificationService.queueNotification(new NotificationDTO(message, phoneNumber, type));
        }
    }

    private String issueCredential(Visitor visitor) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        visitor.setCredentialHash(sha256(code)); visitor.setCredentialHint(code.substring(code.length() - 6));
        return code;
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String normalizePlate(String plate) {
        return StringUtils.defaultString(plate).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private Users requireUser() {
        Users user = userDao.getUserObject();
        if (user == null) throw new PMSCustomException(ResponseCode.COULD_NOT_FIND_USER_SESSION);
        return user;
    }
}
