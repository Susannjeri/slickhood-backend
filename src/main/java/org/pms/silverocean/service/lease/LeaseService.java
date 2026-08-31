package org.pms.silverocean.service.lease;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.controller.wrappers.LeaseMessageRequest;
import org.pms.silverocean.database.pms.entities.Invite;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.LeaseCharge;
import org.pms.silverocean.database.pms.entities.LeaseMessage;
import org.pms.silverocean.database.pms.entities.LeaseTemplate;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.RoleService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.auth.wrappers.RoleWrapper;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.config.enums.PMSConfigs;
import org.pms.silverocean.service.invites.InviteDao;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.lease.wrappers.LeaseContextDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseMessageDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseTemplateDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseTerminationRequest;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.lease.wrappers.TenancyProjection;
import org.pms.silverocean.service.mustache.RenderService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.service.property.UnitDao;
import org.pms.silverocean.service.property.charges.PMSChargeTypes;
import org.pms.silverocean.service.property.charges.PMSPeriod;
import org.pms.silverocean.service.property.wrappers.PropertyNameAddressAndTypeProjection;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.pms.silverocean.service.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_LEASE_TEMPLATE;

@Service
@Slf4j
public class LeaseService {
    private final LeaseDao leaseDao;
    private final UserDao userDao;
    private final UnitDao unitDao;
    private final LeaseMessageDao leaseMessageDao;
    private final ConfigService configService;
    private final I18NService i18NService;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;

    private final RenderService renderService;

    private final RoleService roleService;

    private final InviteDao inviteDao;
    private final LeaseTemplateDao leaseTemplateDao;

    static final String DEFAULT_PREFIX = "DEFAULT_";

    public LeaseService(LeaseDao leaseDao, UserDao userDao, UnitDao unitDao, LeaseMessageDao leaseMessageDao, ConfigService configService, I18NService i18NService, EncryptionService encryptionService, NotificationService notificationService, RenderService renderService, RoleService roleService, InviteDao inviteDao, LeaseTemplateDao leaseTemplateDao) {
        this.leaseDao = leaseDao;
        this.userDao = userDao;
        this.unitDao = unitDao;
        this.leaseMessageDao = leaseMessageDao;
        this.configService = configService;
        this.i18NService = i18NService;
        this.encryptionService = encryptionService;
        this.notificationService = notificationService;
        this.renderService = renderService;
        this.roleService = roleService;
        this.inviteDao = inviteDao;
        this.leaseTemplateDao = leaseTemplateDao;
    }

    @PostConstruct
    public void init() {
        checkAndInitializeDefaultTemplates();
    }

    public List<TenancyProjection> listTenancyPerLoggedInUser() {
        return leaseDao.getTenancyByUserId(userDao.getUserId());
    }

    @Transactional
    public void tenantEditLease(long leaseId, LocalDate moveInDate, LocalDate moveOutDate) {
        validateLeaseDates(moveInDate, moveOutDate);
        Lease lease = leaseDao.getLeaseByIdAndTenantId(leaseId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        if (lease.isSigned()) {
            throw new PMSCustomException(ResponseCode.ERROR_LEASE_ALREADY_ACCEPTED);
        }
        lease.setMoveInDate(moveInDate);
        lease.setMoveOutDate(moveOutDate);
        leaseDao.saveLease(lease, Permission.EDIT_LEASE);
    }

    @Transactional
    public void deleteLease(long leaseId) {
        Lease lease = leaseDao.getLeaseByIdAndStaffOwnerOrTenantId(leaseId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        if (lease.isSigned()) {
            throw new PMSCustomException(ResponseCode.ERROR_LEASE_ALREADY_ACCEPTED);
        }
        lease.setActive(false);
        leaseDao.saveLease(lease, Permission.DELETE_LEASE);
    }

    @Transactional
    public void ownerEditLease(long leaseId, LeaseTemplateDTO leaseTemplateDTO) {
        Lease lease = leaseDao.getLeaseByIdAndStaffOwner(leaseId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        if (lease.isSigned()) {
            throw new PMSCustomException(ResponseCode.ERROR_LEASE_ALREADY_ACCEPTED);
        }

        lease.setRepairThreshold(leaseTemplateDTO.repairThreshold());
        lease.setSelfRenew(leaseTemplateDTO.selfRenew());
        lease.setLeaseDurationInMonths(leaseTemplateDTO.leaseDurationInMonths());
        lease.setNoticePeriodInMonths(leaseTemplateDTO.noticePeriodInMonths());
        lease.setDepositReturnDays(leaseTemplateDTO.depositReturnDays());
        lease.setRentDueDayOfMonth(leaseTemplateDTO.rentDueDayOfMonth());
        lease.setEntryNoticeDays(leaseTemplateDTO.entryNoticeDays());
        lease.setPetsPolicy(leaseTemplateDTO.petsPolicy().getBytes());
        leaseDao.saveLease(lease, Permission.EDIT_LEASE);
    }

    @Transactional
    public void initializeLeaseDraft(String token, LocalDate moveInDate, LocalDate moveOutDate) {
        validateLeaseDates(moveInDate, moveOutDate);
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }

        leaseDao.getLeaseFromTokenAndUser(token, user.getId())
                .map(invite -> {
                    throw new PMSCustomException(ResponseCode.LEASE_ALREADY_EXISTS);
                });
        Unit unit = unitDao.findByToken(token).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_INVITE_LINK));
        Invite invite = inviteDao.getInviteByToken(token, true)
                .filter(candidate -> InviteType.TENANT.name().equals(candidate.getType()))
                .filter(candidate -> candidate.getExpiryDate() == null || LocalDateTime.now().isBefore(candidate.getExpiryDate()))
                .orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN));
        if (!recipientMatches(invite.getRecipient(), user)) {
            throw new PMSCustomException(ResponseCode.INVALID_USER_DETAILS);
        }
        LeaseTemplate leaseTemplate = leaseTemplateDao
                .getTemplateById(unit.getTemplateId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND));

        UnitTenant unitTenant = new UnitTenant();
        unitTenant.setInviteId(invite.getId());
        unitTenant.setUserId(user.getId());
        unitTenant.setUnitId(unit.getId());
        unitTenant.setActive(true);
        leaseDao.saveUnitTenant(unitTenant);

        Lease lease = leaseTemplate.initLeaseFromTemplate();
        lease.setTenantId(unitTenant.getId());
        lease.setLeaseDate(LocalDate.now());
        lease.setMoveInDate(moveInDate);
        lease.setMoveOutDate(moveOutDate);
        lease.setPrice(unit.getPrice());
        lease.setCurrency(unit.getCurrency());
        lease.setCreatedBy(user.getId());
        lease.setLastModifiedDate(LocalDateTime.now());
        lease.setActive(true);
        lease.setGovernedDocumentRequired(true);

        leaseDao.createLease(lease);

        List<LeaseCharge> unitCharges = unitDao.getAllUnitCharges(unit.getId()).stream().map(unitCharge -> {
            LeaseCharge leaseCharge = new LeaseCharge();
            leaseCharge.setLeaseId(lease.getId());
            leaseCharge.setChargeId(unitCharge.getChargeId());
            leaseCharge.setAmount(unitCharge.getAmount());
            leaseCharge.setPeriod(unitCharge.getPeriod());
            leaseCharge.setActive(true);
            return leaseCharge;
        }).toList();
        if (!CollectionUtils.isEmpty(unitCharges)) {
            leaseDao.saveAllLeaseCharges(unitCharges);
            lease.setCharges(true);
            leaseDao.saveLease(lease, Permission.EDIT_LEASE_CHARGES);
        }
        invite.setActive(false);
        inviteDao.updateInvite(invite);
    }

    public Page<LeaseDTO> getLeaseList(Pageable pageable) {
        long userId = userDao.getUserId();
        boolean privileged = userDao.hasRole(PMSRole.SUPER_ADMIN);
        return leaseDao.getLeaseList(userId, privileged, bounded(pageable));
    }

    @Transactional
    public Page<LeaseMessageDTO> getLeaseMessageByLeaseId(Pageable pageable, long leaseId) {
        Lease lease = leaseDao.getLeaseByIdAndStaffOwnerOrTenantId(leaseId, userDao.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        return leaseMessageDao.getLeaseMessagesByLeaseId(bounded(pageable), leaseId).map(leaseMessage -> {
            String decrypted = encryptionService.decrypt(leaseMessage.message().getBytes()).decryptedValue();
            return leaseMessage.withDecryptedMessage(decrypted);
        });
    }


    @Transactional
    public void sendLeaseMessage(LeaseMessageRequest request) {
        Users currentUser = userDao.getUserObject();
        Long currentUserId = currentUser.getId();

        LeaseContextDTO leaseContext = leaseDao.getContextToPrepLeaseMessage(request.leaseId(), currentUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        String userNameAndTitle = String.format("%s, (%s)", currentUser.getFullName(), leaseContext.senderRoleName());
        String formattedMessage = String.format(
                i18NService.getLocalizedMessage(NotificationType.LEASE_MESSAGE_EMAIL.getBody()),
                userNameAndTitle,
                leaseContext.propertyName(),
                request.message()
        );

        // 3. Notify Relevant Parties (Tenant, Landlord, Managers)
        Set<String> recipientEmails = new HashSet<>();

        // Add Tenant if sender is NOT the tenant
        if (!PMSRole.TENANT.name().equals(leaseContext.senderRoleName())) {
            userDao.findById(leaseContext.tenantUserId()).ifPresent(t -> recipientEmails.add(t.getEmail()));
        }

        // Add Landlord if sender is NOT the landlord
        if (!currentUserId.equals(leaseContext.landlordUserId())) {
            userDao.findById(leaseContext.landlordUserId()).ifPresent(l -> recipientEmails.add(l.getEmail()));
        }

        // Add Property Managers (excluding sender)
        roleService.getPropertyManagersDetailsByUnitId(leaseContext.unitId()).stream()
                .filter(pm -> !pm.getId().equals(currentUserId))
                .forEach(pm -> recipientEmails.add(pm.getEmail()));

        // 4. Batch Send Notifications
        recipientEmails.forEach(email ->
                notificationService.queueNotification(new NotificationDTO(formattedMessage, email, NotificationType.LEASE_MESSAGE_EMAIL))
        );
        // 1. Persist the message
        LeaseMessage leaseMessage = new LeaseMessage();
        leaseMessage.setMessage(encryptionService.encrypt(request.message()));
        leaseMessage.setLeaseId(request.leaseId());
        leaseMessage.setCreatedBy(currentUserId);
        leaseMessageDao.save(leaseMessage);
    }

    private PMSRole determineSenderRole(Long userId, UnitTenant unitTenant) {
        if (unitTenant.getUserId() == userId) return PMSRole.TENANT;
        if (roleService.checkIfStaffInProperty(userId, unitTenant.getUnitId())) return PMSRole.PROPERTY_MANAGER;
        return PMSRole.LANDLORD;
    }

    private Pageable bounded(Pageable pageable) {
        return PageRequest.of(Math.max(0, pageable.getPageNumber()),
                Math.min(100, Math.max(1, pageable.getPageSize())), pageable.getSort());
    }

    private void validateLeaseDates(LocalDate moveInDate, LocalDate moveOutDate) {
        if (moveInDate == null || moveOutDate == null || !moveOutDate.isAfter(moveInDate)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
        }
    }

    private boolean recipientMatches(String recipient, Users user) {
        if (recipient == null || recipient.isBlank()) return true;
        if (recipient.equalsIgnoreCase(user.getEmail())) return true;
        if (recipient.contains("@")) return false;
        String expectedPhone = PMSUtils.getLocalisedPhoneNumber(recipient);
        String actualPhone = PMSUtils.getLocalisedPhoneNumber(user.getPhoneNumber());
        return expectedPhone != null && expectedPhone.equals(actualPhone);
    }

    @Transactional
    public void signLease(long leaseId) {
        Lease lease = leaseDao.getLeaseByIdAndStaffOwnerOrTenantId(leaseId, userDao.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        if (lease.isSigned()) {
            return;
        }
        if (lease.isGovernedDocumentRequired()) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        UnitTenant unitTenant = leaseDao.getUnitTenantByTenantId(lease.getTenantId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = unitDao.findByAndLockById(unitTenant.getUnitId()).orElseThrow(() -> new PMSCustomException(ResponseCode.GENERAL_FAILURE));
        long propertyId = unit.getPropertyId();
        List<RoleWrapper> permissionsForUser = roleService.getPermissionsForUser(userDao.getUserId()).stream().filter(roleWrapper ->
                roleWrapper.getProperty().contains(propertyId)
        ).toList();

        permissionsForUser.forEach(roleWrapper -> {
            if (PMSRole.LANDLORD.getName().equals(roleWrapper.getRoleName()) && lease.getManagerSignedDate() == null) {
                lease.setSignedByManagerId(userDao.getUserId());
                lease.setManagerSignedDate(LocalDateTime.now());
            }
            if (PMSRole.PROPERTY_MANAGER.getName().equals(roleWrapper.getRoleName()) && lease.getManagerSignedDate() == null) {
                lease.setSignedByManagerId(userDao.getUserId());
                lease.setManagerSignedDate(LocalDateTime.now());
            }
            if (PMSRole.TENANT.getName().equals(roleWrapper.getRoleName()) && lease.getTenantSignedDate() == null) {
                lease.setTenantSignedDate(LocalDateTime.now());
            }
        });

        activateWhenFullySigned(lease, unitTenant, unit);
        leaseDao.deleteUnsignedLeaseAndUnitTenantsByUnitIdAndLeaseId(unit.getId(), lease.getId());
        leaseDao.saveLease(lease, Permission.SIGN_LEASE);
    }

    @Transactional
    public void activateFromGovernedAgreement(long leaseId, long issuerUserId, long recipientUserId,
                                              LocalDateTime issuerSignedAt, LocalDateTime recipientSignedAt) {
        Lease lease = leaseDao.getLeaseById(leaseId)
                .filter(Lease::isActive).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = leaseDao.getUnitByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        if (tenancy.getUserId() != recipientUserId || !roleService.checkIfStaffInProperty(issuerUserId, unit.getId())
                && !unitDao.findPropertyOwnerId(unit.getId()).filter(id -> id == issuerUserId).isPresent()) {
            throw new PMSCustomException(ResponseCode.LEASE_DOCUMENT_INVALID_STATE);
        }
        lease.setTenantSignedDate(recipientSignedAt);
        lease.setManagerSignedDate(issuerSignedAt);
        lease.setSignedByManagerId(issuerUserId);
        activateWhenFullySigned(lease, tenancy, unit);
        leaseDao.deleteUnsignedLeaseAndUnitTenantsByUnitIdAndLeaseId(unit.getId(), lease.getId());
        leaseDao.saveLease(lease, Permission.SIGN_LEASE);
    }

    private void activateWhenFullySigned(Lease lease, UnitTenant tenancy, Unit unit) {
        if (lease.getTenantSignedDate() == null || lease.getManagerSignedDate() == null) return;
        lease.setSigned(true);
        lease.setLifecycleStatus("ACTIVE");
        tenancy.setLeaseAccepted(true);
        leaseDao.saveUnitTenant(tenancy);
        unit.setOccupied(true);
        unitDao.update(unit);
        lease.setNextPaymentDate(firstRentDueDate(lease));
        lease.setPaymentDue(true);
        if (lease.isCharges()) leaseDao.updateSignedLeaseCharges(lease.getId());
    }

    @Transactional
    public LeaseDTO requestTermination(long leaseId, LeaseTerminationRequest request) {
        long userId = userDao.getUserId();
        Lease lease = leaseDao.getLeaseByIdAndStaffOwnerOrTenantId(leaseId, userId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        if (!lease.isSigned() || "TERMINATED".equals(lease.getLifecycleStatus())) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
        }
        int noticeMonths = Optional.ofNullable(lease.getNoticePeriodInMonths()).orElse(0);
        LocalDate earliest = LocalDate.now(PMSUtils.getZoneId()).plusMonths(noticeMonths);
        if (request.effectiveDate().isBefore(earliest)) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA_CONSTRAINT);
        }
        lease.setLifecycleStatus("NOTICE_GIVEN");
        lease.setTerminationEffectiveDate(request.effectiveDate());
        lease.setTerminationReason(request.reason().trim());
        lease.setTerminationRequestedBy(userId);
        lease.setTerminationRequestedAt(LocalDateTime.now());
        leaseDao.saveLease(lease, Permission.DELETE_LEASE);
        notifyLeaseParties(lease, request);
        return new LeaseDTO(lease, userDao.findById(leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND)).getUserId())
                .map(Users::getFullName).orElse(null), null);
    }

    @Scheduled(cron = "${pms.lease.termination.cron:0 15 * * * *}")
    @Transactional
    public void finalizeDueTerminations() {
        LocalDate today = LocalDate.now(PMSUtils.getZoneId());
        for (Lease lease : leaseDao.getExpiryCandidates(today, PageRequest.of(0, 200))) {
            if (lease.isSelfRenew() && lease.getLeaseDurationInMonths() != null && lease.getLeaseDurationInMonths() > 0) {
                LocalDate renewedUntil = lease.getMoveOutDate();
                do { renewedUntil = renewedUntil.plusMonths(lease.getLeaseDurationInMonths()); }
                while (!renewedUntil.isAfter(today));
                lease.setMoveOutDate(renewedUntil);
                leaseDao.saveLease(lease, Permission.EDIT_LEASE);
                notifyRenewal(lease);
            } else {
                LeaseTerminationRequest expiry = new LeaseTerminationRequest(today, "Lease term expired");
                lease.setLifecycleStatus("NOTICE_GIVEN");
                lease.setTerminationEffectiveDate(today);
                lease.setTerminationReason(expiry.reason());
                lease.setTerminationRequestedAt(LocalDateTime.now());
                leaseDao.saveLease(lease, Permission.DELETE_LEASE);
                notifyLeaseParties(lease, expiry);
            }
        }
        for (Lease lease : leaseDao.getTerminationCandidates(today, PageRequest.of(0, 200))) {
            UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId()).orElse(null);
            Unit unit = leaseDao.getUnitByTenantId(lease.getTenantId()).orElse(null);
            lease.setLifecycleStatus("TERMINATED");
            lease.setPaymentDue(false);
            lease.setActive(false);
            leaseDao.saveLease(lease, Permission.DELETE_LEASE);
            if (tenancy != null) {
                tenancy.setLeaseAccepted(false);
                tenancy.setActive(false);
                leaseDao.saveUnitTenant(tenancy);
            }
            if (unit != null) {
                unit.setOccupied(false);
                unitDao.update(unit);
            }
        }
    }

    private void notifyRenewal(Lease lease) {
        UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = leaseDao.getUnitByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        long ownerId = unitDao.findPropertyOwnerId(unit.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        String body = String.format(i18NService.getLocalizedMessage(NotificationType.LEASE_RENEWAL_EMAIL.getBody()),
                lease.getId(), lease.getMoveOutDate());
        new HashSet<>(List.of(tenancy.getUserId(), ownerId)).forEach(id -> userDao.findById(id)
                .map(Users::getEmail).filter(email -> email != null && !email.isBlank())
                .ifPresent(email -> notificationService.queueNotification(
                        new NotificationDTO(body, email, NotificationType.LEASE_RENEWAL_EMAIL))));
    }

    private LocalDate firstRentDueDate(Lease lease) {
        LocalDate moveIn = Optional.ofNullable(lease.getMoveInDate()).orElse(LocalDate.now(PMSUtils.getZoneId()));
        int requestedDay = Optional.ofNullable(lease.getRentDueDayOfMonth()).orElse(moveIn.getDayOfMonth());
        LocalDate due = moveIn.withDayOfMonth(Math.min(requestedDay, moveIn.lengthOfMonth()));
        if (due.isBefore(moveIn)) {
            LocalDate next = moveIn.plusMonths(1);
            due = next.withDayOfMonth(Math.min(requestedDay, next.lengthOfMonth()));
        }
        return due;
    }

    private void notifyLeaseParties(Lease lease, LeaseTerminationRequest request) {
        UnitTenant tenancy = leaseDao.getUnitTenantByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        Unit unit = leaseDao.getUnitByTenantId(lease.getTenantId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));
        String body = String.format(i18NService.getLocalizedMessage(NotificationType.LEASE_TERMINATION_EMAIL.getBody()),
                lease.getId(), request.effectiveDate(), request.reason().trim());
        long ownerId = unitDao.findPropertyOwnerId(unit.getId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        new HashSet<>(List.of(tenancy.getUserId(), ownerId)).forEach(id -> userDao.findById(id)
                .map(Users::getEmail).filter(email -> email != null && !email.isBlank())
                .ifPresent(email -> notificationService.queueNotification(
                        new NotificationDTO(body, email, NotificationType.LEASE_TERMINATION_EMAIL))));
    }

    @Transactional
    public void viewLease(long leaseId, OutputStream outputStream) {
        Lease lease = leaseDao.getLeaseByIdAndStaffOwnerOrTenantId(leaseId, userDao.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        UnitTenant unitTenant = leaseDao.getUnitTenantByTenantId(lease.getTenantId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        PropertyNameAddressAndTypeProjection propertyDetails = unitDao.getPropertyDetailsFromUnitId(unitTenant.getUnitId()).orElseThrow();

        List<UnitChargeProjection> leaseCharges = leaseDao.getLeaseChargeByLeaseId(lease.getId());

        renderLeaseTemplateInternal(
                lease,
                null,
                unitTenant.getUserId(),
                leaseCharges,
                propertyDetails,
                outputStream
        );
    }

    public void renderLeaseTemplateToPdfByUnit(Long unitId, OutputStream outputStream) throws IOException {
        Unit unit = unitDao.findByIdAndStaffOrOwner(unitId, userDao.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        LeaseTemplate leaseTemplate = leaseTemplateDao
                .getTemplateById(unit.getTemplateId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_NOT_FOUND));

        PropertyNameAddressAndTypeProjection propertyDetails =
                unitDao.getPropertyDetailsFromUnitId(unitId).orElse(null);

        List<UnitChargeProjection> leaseCharges =
                unitDao.getUnitCharges(unitId);

        renderLeaseTemplateInternal(
                leaseTemplate,
                unit,
                null,
                leaseCharges,
                propertyDetails,
                outputStream
        );
    }

    public void renderLeaseTemplateToPdfByTemplate(Long templateId, OutputStream outputStream) throws IOException {
        LeaseTemplate leaseTemplate;
        Optional<LeaseTemplate> leaseTemplateContainer = leaseTemplateDao
                .findByIdAndStaffOrOwner(templateId, userDao.getUserId());
        if (leaseTemplateContainer.isEmpty()) {
            leaseTemplate = leaseTemplateDao.getTemplateById(templateId).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND));
            if (!(DEFAULT_PREFIX + PMSLeaseMode.RENT.name()).equals(leaseTemplate.getName()) && !(DEFAULT_PREFIX + PMSLeaseMode.SALE.name()).equals(leaseTemplate.getName())) {
                throw new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND);
            }
        } else {
            leaseTemplate = leaseTemplateContainer.get();
        }


        List<UnitChargeProjection> leaseCharges =
                PMSLeaseMode.valueOf(leaseTemplate.getLeaseMode())
                        .getDefaultCharges();

        renderLeaseTemplateInternal(
                leaseTemplate,
                null,
                null,
                leaseCharges,
                null,
                outputStream
        );
    }

    public void renderLeaseTemplateToPdfByInviteToken(String inviteToken, OutputStream outputStream) {
        Unit unit = unitDao.findByToken(inviteToken).orElseThrow(() -> new PMSCustomException(ResponseCode.INVALID_OR_EXPIRED_TOKEN));
        LeaseTemplate leaseTemplate = leaseTemplateDao
                .getTemplateById(unit.getTemplateId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND));
        PropertyNameAddressAndTypeProjection propertyDetails =
                unitDao.getPropertyDetailsFromUnitId(unit.getId()).orElse(null);
        List<UnitChargeProjection> unitCharges = unitDao.getUnitCharges(unit.getId());
        Long tenantId = null;

        if (userDao.getUserId() != null && userDao.hasRole(PMSRole.TENANT)) {
            tenantId = userDao.getUserId();
        }
        renderLeaseTemplateInternal(
                leaseTemplate,
                unit,
                tenantId,
                unitCharges,
                propertyDetails,
                outputStream
        );
    }

    private void renderLeaseTemplateInternal(
            Object leaseTemplate,
            Unit unit,
            Long tenantId,
            List<UnitChargeProjection> leaseCharges,
            PropertyNameAddressAndTypeProjection propertyDetails,
            OutputStream outputStream
    ) {

        Map<String, Object> data = leaseTemplate instanceof LeaseTemplate ? buildLeaseViewData(
                (LeaseTemplate) leaseTemplate,
                unit,
                tenantId,
                leaseCharges,
                propertyDetails
        ) : buildLeaseViewData((Lease) leaseTemplate, tenantId, leaseCharges, propertyDetails);

        String htmlContent = renderService.render(configService.getConfigByName(PMSConfigs.LEASE_TEMPLATE_FILE).get().stringValue(), data);
        try {
            renderService.toPdf(htmlContent, outputStream);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            throw new PMSCustomException(ResponseCode.GENERAL_FAILURE, ex);
        }
    }


    public void createLeaseTemplate(LeaseTemplateDTO leaseTemplateDTO) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }

        if (leaseTemplateDao.getTemplateByNameAndCreatedBy(leaseTemplateDTO.name(), userDao.getUserId()).isPresent()) {
            throw new PMSCustomException(ResponseCode.DUPLICATE_LEASE_TEMPLATE_NAME);
        }
        LeaseTemplate leaseTemplate = new LeaseTemplate();
        leaseTemplate.setName(leaseTemplateDTO.name());
        leaseTemplate.setLeaseMode(leaseTemplateDTO.leaseMode().name());
        leaseTemplate.setRepairThreshold(leaseTemplateDTO.repairThreshold());
        leaseTemplate.setSelfRenew(leaseTemplateDTO.selfRenew());
        leaseTemplate.setLeaseDurationInMonths(leaseTemplateDTO.leaseDurationInMonths());
        leaseTemplate.setNoticePeriodInMonths(leaseTemplateDTO.noticePeriodInMonths());
        leaseTemplate.setDepositReturnDays(leaseTemplateDTO.depositReturnDays());
        leaseTemplate.setRentDueDayOfMonth(leaseTemplateDTO.rentDueDayOfMonth());
        leaseTemplate.setEntryNoticeDays(leaseTemplateDTO.entryNoticeDays());
        leaseTemplate.setPetsPolicy(leaseTemplateDTO.petsPolicy().getBytes());
        leaseTemplate.setActive(true);
        leaseTemplate.setCreatedBy(user.getId());
        leaseTemplate.setLastModifiedDate(LocalDateTime.now());
        leaseTemplateDao.saveTemplate(leaseTemplate, CREATE_LEASE_TEMPLATE);
    }

    public void deleteLeaseTemplate(long leaseTemplateId) {
        LeaseTemplate leaseTemplate = leaseTemplateDao.getTemplateByIdAndCreatedBy(leaseTemplateId, userDao.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND));
        leaseTemplate.setActive(false);
        leaseTemplate.setLastModifiedDate(LocalDateTime.now());
        leaseTemplateDao.saveTemplate(leaseTemplate, DELETE_LEASE_TEMPLATE);
    }

    public void editLeaseTemplate(long leaseTemplateId, LeaseTemplateDTO leaseTemplateDTO) {
        LeaseTemplate leaseTemplate = leaseTemplateDao.getTemplateByIdAndCreatedBy(leaseTemplateId, userDao.getUserId()).orElseThrow(() -> new PMSCustomException(ResponseCode.LEASE_TEMPLATE_NOT_FOUND));
        if (!leaseTemplate.getName().equals(leaseTemplateDTO.name())) {
            if (leaseTemplateDao.getTemplateByNameAndCreatedBy(leaseTemplateDTO.name(), userDao.getUserId()).isPresent()) {
                throw new PMSCustomException(ResponseCode.DUPLICATE_LEASE_TEMPLATE_NAME);
            }
            leaseTemplate.setName(leaseTemplateDTO.name());
        }


        leaseTemplate.setLeaseMode(leaseTemplateDTO.leaseMode().name());
        leaseTemplate.setRepairThreshold(leaseTemplateDTO.repairThreshold());
        leaseTemplate.setSelfRenew(leaseTemplateDTO.selfRenew());
        leaseTemplate.setLeaseDurationInMonths(leaseTemplateDTO.leaseDurationInMonths());
        leaseTemplate.setNoticePeriodInMonths(leaseTemplateDTO.noticePeriodInMonths());
        leaseTemplate.setDepositReturnDays(leaseTemplateDTO.depositReturnDays());
        leaseTemplate.setRentDueDayOfMonth(leaseTemplateDTO.rentDueDayOfMonth());
        leaseTemplate.setEntryNoticeDays(leaseTemplateDTO.entryNoticeDays());
        leaseTemplate.setPetsPolicy(leaseTemplateDTO.petsPolicy().getBytes());
        leaseTemplate.setLastModifiedDate(LocalDateTime.now());
        leaseTemplateDao.saveTemplate(leaseTemplate, EDIT_LEASE_TEMPLATE);
    }

    private void populateUnitFees(List<UnitChargeProjection> unitCharges, String currency, Map<String, Object> data) {
        for (UnitChargeProjection unitCharge : unitCharges) {
            String periodName = i18NService.getLocalizedMessage(PMSPeriod.valueOf(unitCharge.getPeriodId()).getName());
            final String formattedAmount = String.format("%s %, .2f", currency, unitCharge.getAmount());
            switch (PMSChargeTypes.valueOf(unitCharge.getChargeName())) {
                case SERVICE -> {
                    data.put("hasServiceCharge", true);
                    data.put("service_charge_amount", formattedAmount);
                    data.put("service_charge_period", periodName);
                }
                case WATER -> {
                    data.put("hasWaterFees", true);
                    data.put("water_fee_amount", formattedAmount);
                    data.put("water_fee_unit", "m³");
                    data.put("water_fee_period", periodName);
                }
                case DEPOSIT -> {
                    data.put("hasSecurityDeposit", true);
                    data.put("security_deposit", formattedAmount);
                }
                case GARBAGE -> {
                    data.put("hasGarbageFees", true);
                    data.put("garbage_fee_amount", formattedAmount);
                    data.put("garbage_fee_period", periodName);
                }
                case PARKING -> {
                    data.put("hasParkingFees", true);
                    data.put("parking_fee_amount", formattedAmount);
                    data.put("parking_fee_unit", "vehicle");
                    data.put("parking_fee_period", periodName);
                }
                case SECURITY -> {
                    data.put("hasSecurityFees", true);
                    data.put("security_fee_amount", formattedAmount);
                    data.put("security_fee_period", periodName);
                }
                case LATE_FEES -> {
                    data.put("hasLateFees", true);
                    data.put("late_fee_amount", formattedAmount);
                    data.put("late_fee_period", periodName);
                }
                case ELECTRICITY -> {
                    data.put("hasElectricityFees", true);
                    data.put("electricity_fee_amount", formattedAmount);
                    data.put("electricity_fee_unit", "kWh");
                    data.put("electricity_fee_period", periodName);
                }
            }

        }
    }

    public Page<LeaseTemplateDTO> getTemplateList(Pageable pageable, PMSLeaseMode leaseMode) {
        Users user = userDao.getUserObject();
        if (!user.isCompletedProfile()) {
            throw new PMSCustomException(ResponseCode.INCOMPLETE_USER_PROFILE, user.getProfileCompletenessState());
        }

        Page<LeaseTemplate> leaseTemplatesByCreatedByAndLeaseMode = leaseMode != null ? leaseTemplateDao.getLeaseTemplatesByCreatedByAndLeaseMode(pageable, user.getId(), leaseMode) :
                leaseTemplateDao.getLeaseTemplatesByCreatedBy(pageable, user.getId());
        return leaseTemplatesByCreatedByAndLeaseMode
                .map(templateFromDb -> new LeaseTemplateDTO(templateFromDb.getId(), templateFromDb.getName(), PMSLeaseMode.valueOf(templateFromDb.getLeaseMode()),
                        templateFromDb.isSelfRenew(), templateFromDb.getLeaseDurationInMonths(), templateFromDb.getNoticePeriodInMonths(),
                        templateFromDb.getDepositReturnDays(), templateFromDb.getRentDueDayOfMonth(), templateFromDb.getEntryNoticeDays(), templateFromDb.getRepairThreshold(),
                        new String(templateFromDb.getPetsPolicy())));
    }


    public Map<String, Object> buildLeaseViewData(
            LeaseTemplate leaseTemplate,
            Unit unit,
            Long tenantId,
            List<UnitChargeProjection> unitCharges,
            PropertyNameAddressAndTypeProjection propertyDetails
    ) {
        Map<String, Object> data = new HashMap<>();

        populateCommonFields(data, leaseTemplate);

        // LeaseTemplate-specific defaults
        data.put("leaseAccepted", false);
        data.put("lease_date", LocalDate.now());
        data.put("move_in_date", LocalDate.now().plusWeeks(2));
        data.put("move_out_date", LocalDate.now().plusYears(2));
        long createdBy = leaseTemplate.getCreatedBy() == null ? userDao.getUserId() == null ? unit.getCreatedBy() : userDao.getUserId() : leaseTemplate.getCreatedBy();
        if (unit == null) {
            data.put("price", "KES 40,000.00");
            data.put("unit_ref", "*****");
        } else {
            createdBy = unit.getCreatedBy();
            data.put("price", String.format("%s %, .2f", unit.getCurrency(), unit.getPrice()));
            data.put("unit_ref", unit.getRef());
        }
        populateTenantLandlordAndProperty(data, tenantId, createdBy, propertyDetails);

        // Charges
        data.put("hasCharges", !CollectionUtils.isEmpty(unitCharges));
        populateUnitFees(unitCharges, "KES", data);

        return data;
    }

    // ---------------- Overload for Lease ----------------
    public Map<String, Object> buildLeaseViewData(
            Lease lease,
            Long tenantId,
            List<UnitChargeProjection> unitCharges,
            PropertyNameAddressAndTypeProjection propertyDetails
    ) {
        Map<String, Object> data = new HashMap<>();

        populateCommonFields(data, lease); // Use Lease fields (template fields copied into Lease)
        Optional<Unit> unit = leaseDao.getUnitByTenantId(lease.getTenantId());

        // Lease-specific info
        data.put("leaseAccepted", lease.isSigned());
        data.put("unit_ref", unit.map(Unit::getRef).orElse("****"));
        data.put("lease_date", lease.getLeaseDate());
        data.put("move_in_date", lease.getMoveInDate());
        data.put("move_out_date", lease.getMoveOutDate());
        data.put("price", String.format("%s %, .2f", lease.getCurrency(), lease.getPrice()));

        Long createdBy = unit.map(Unit::getCreatedBy).orElse(null);
        populateTenantLandlordAndProperty(data, tenantId, createdBy, propertyDetails);

        // Charges
        data.put("hasCharges", !CollectionUtils.isEmpty(unitCharges));
        populateUnitFees(unitCharges, lease.getCurrency(), data);

        return data;
    }

    // ---------------- Helper: populate common template/lease fields ----------------
    private void populateCommonFields(Map<String, Object> data, Object source) {
        String leaseMode;
        boolean selfRenew;
        Integer leaseDuration, rentDue, noticePeriod, depositReturnDays, entryNoticeDays;
        byte[] petsPolicy;
        Double repairThreshold;
        String currency;

        if (source instanceof LeaseTemplate t) {
            leaseMode = t.getLeaseMode();
            selfRenew = t.isSelfRenew();
            leaseDuration = t.getLeaseDurationInMonths();
            rentDue = t.getRentDueDayOfMonth();
            noticePeriod = t.getNoticePeriodInMonths();
            depositReturnDays = t.getDepositReturnDays();
            entryNoticeDays = t.getEntryNoticeDays();
            petsPolicy = t.getPetsPolicy();
            repairThreshold = t.getRepairThreshold();
            currency = "KES";
        } else if (source instanceof Lease l) {
            leaseMode = l.getLeaseMode();
            selfRenew = l.isSelfRenew();
            leaseDuration = l.getLeaseDurationInMonths();
            rentDue = l.getRentDueDayOfMonth();
            noticePeriod = l.getNoticePeriodInMonths();
            depositReturnDays = l.getDepositReturnDays();
            entryNoticeDays = l.getEntryNoticeDays();
            petsPolicy = l.getPetsPolicy();
            repairThreshold = l.getRepairThreshold();
            currency = l.getCurrency();
        } else {
            throw new IllegalArgumentException("Unsupported source type");
        }

        PMSLeaseMode modeEnum = PMSLeaseMode.valueOf(leaseMode);
        data.put("isRental", PMSLeaseMode.RENT.equals(modeEnum));
        data.put("selfRenewable", selfRenew);
        data.put("lease_duration", leaseDuration);
        data.put("rent_due_date", rentDue);
        data.put("notice_period", noticePeriod);
        data.put("deposit_return_days", depositReturnDays);
        data.put("entry_notice_days", entryNoticeDays);
        data.put("pets_policy", new String(petsPolicy));
        data.put("repair_threshold", String.format("%s %, .2f", currency, repairThreshold));
    }

    // ---------------- Helper: populate tenant/landlord/property ----------------
    private void populateTenantLandlordAndProperty(Map<String, Object> data,
                                                   Long tenantId,
                                                   Long landlordId,
                                                   PropertyNameAddressAndTypeProjection propertyDetails) {
        // Landlord
        if (landlordId != null) {
            userDao.findById(landlordId).ifPresent(landlord -> {
                data.put("landlord_name", Optional.ofNullable(landlord.getFullName()).orElse("****"));
                data.put("landlord_contact", landlord.getEmail());
            });
        }

        // Tenant
        if (tenantId != null) {
            userDao.findById(tenantId).ifPresent(tenant -> {
                data.put("tenant_name", Optional.ofNullable(tenant.getFullName()).orElse("****"));
                data.put("tenant_contact", tenant.getEmail());
            });
        } else {
            data.put("tenant_name", "****");
            data.put("tenant_contact", "****");
        }

        // Property
        if (propertyDetails != null) {
            data.put("property_name", propertyDetails.getName());
            data.put("property_address", propertyDetails.getAddress());
            data.put("property_type", propertyDetails.getType());
        } else {
            data.put("property_name", "****");
            data.put("property_address", "****");
            data.put("property_type", "****");
        }
    }


    private void checkAndInitializeDefaultTemplates() {
        initializeDefaultTemplates(PMSLeaseMode.RENT);
        initializeDefaultTemplates(PMSLeaseMode.SALE);
    }

    private void initializeDefaultTemplates(PMSLeaseMode leaseMode) {
        Optional<LeaseTemplate> templateByName = leaseTemplateDao.getTemplateByName(DEFAULT_PREFIX + leaseMode.name());
        if (templateByName.isEmpty()) {
            log.info("Initializing Default Lease");
            leaseTemplateDao.createDefault(leaseMode);
        }
    }


}
