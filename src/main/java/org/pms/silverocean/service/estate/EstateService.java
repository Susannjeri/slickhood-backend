package org.pms.silverocean.service.estate;

import jakarta.transaction.Transactional;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.EstateServiceChargeRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyOwnership;
import org.pms.silverocean.database.pms.entities.EstateServiceCharge;
import org.pms.silverocean.database.pms.entities.PMSInvoice;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.stereotype.Service;
import org.pms.silverocean.service.payment.invoice.InvoiceService;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.notification.NotificationDTO;
import org.pms.silverocean.service.notification.NotificationService;
import org.pms.silverocean.service.notification.common.NotificationType;
import org.pms.silverocean.common.PMSUtils;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.pms.silverocean.service.auth.roles.enums.Permission;

@Service
public class EstateService {
    private final PropertyOwnershipRepo ownershipRepo;
    private final PropertyRepo propertyRepo;
    private final UnitRepo unitRepo;
    private final UserDao userDao;
    private final EstateServiceChargeRepo chargeRepo;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;
    private final I18NService i18n;

    public EstateService(PropertyOwnershipRepo ownershipRepo, PropertyRepo propertyRepo, UnitRepo unitRepo, UserDao userDao,
                         EstateServiceChargeRepo chargeRepo, InvoiceService invoiceService,
                         NotificationService notificationService, I18NService i18n) {
        this.ownershipRepo = ownershipRepo; this.propertyRepo = propertyRepo; this.unitRepo = unitRepo; this.userDao = userDao;
        this.chargeRepo = chargeRepo; this.invoiceService = invoiceService;
        this.notificationService = notificationService; this.i18n = i18n;
    }

    @Transactional
    public PropertyOwnership create(OwnershipRequest request) {
        long userId = userDao.getUserId();
        requireManagedProperty(request.propertyId(), userId);
        Users homeowner = userDao.findById(request.homeownerUserId()).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        if (request.unitId() != null) {
            Unit unit = unitRepo.findAndLockById(request.unitId()).filter(u -> u.isActive() && u.getPropertyId() == request.propertyId())
                    .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
            return createUnitOwnership(unit, homeowner.getId(), request.ownershipStart(), request.source(), userId);
        }
        PropertyOwnership ownership = new PropertyOwnership();
        ownership.setPropertyId(request.propertyId()); ownership.setUnitId(request.unitId());
        ownership.setHomeownerUserId(request.homeownerUserId()); ownership.setOwnershipStart(request.ownershipStart());
        ownership.setSource(request.source()); ownership.setCreatedBy(userId); ownership.setActive(true);
        return ownershipRepo.save(ownership);
    }

    @Transactional
    public PropertyOwnership createOwnershipFromInvite(long unitId, long homeownerUserId, long inviterUserId) {
        Users homeowner = userDao.findById(homeownerUserId).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        Unit unit = unitRepo.findAndLockById(unitId).filter(Unit::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        propertyRepo.findByIdAndStaffOrOwner(unit.getPropertyId(), inviterUserId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
        return createUnitOwnership(unit, homeowner.getId(), LocalDate.now(), "HOMEOWNER_INVITE", inviterUserId);
    }

    private PropertyOwnership createUnitOwnership(Unit unit, long homeownerUserId, LocalDate ownershipStart,
                                                   String source, long createdBy) {
        var current = ownershipRepo.findFirstByUnitIdAndActiveTrue(unit.getId());
        if (current.isPresent() && current.get().getHomeownerUserId() == homeownerUserId) {
            return current.get();
        }
        current.ifPresent(existing -> {
            if (!ownershipStart.isAfter(existing.getOwnershipStart())) {
                throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
            }
            existing.setActive(false);
            existing.setOwnershipEnd(ownershipStart.minusDays(1));
            existing.setTerminationReason("Ownership transferred: " + source);
            existing.setTerminatedBy(createdBy);
            existing.setTerminatedAt(java.time.ZonedDateTime.now(PMSUtils.getZoneId()));
            ownershipRepo.save(existing);
            String propertyName = propertyRepo.findById(unit.getPropertyId()).map(Property::getName)
                    .orElse("property " + unit.getPropertyId());
            notifyOwnershipEnded(existing, propertyName, existing.getOwnershipEnd(), existing.getTerminationReason());
        });
        PropertyOwnership ownership = new PropertyOwnership();
        ownership.setPropertyId(unit.getPropertyId());
        ownership.setUnitId(unit.getId());
        ownership.setHomeownerUserId(homeownerUserId);
        ownership.setOwnershipStart(ownershipStart);
        ownership.setSource(source);
        ownership.setCreatedBy(createdBy);
        ownership.setActive(true);
        return ownershipRepo.save(ownership);
    }

    @Transactional
    public Page<OwnershipView> list(Pageable pageable, Long propertyId, Boolean active) {
        long userId = userDao.getUserId();
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, Math.max(1, pageable.getPageSize())));
        return switch (userDao.getActiveRole()) {
            case HOMEOWNER -> ownershipRepo.findPageByHomeowner(userId, propertyId, active, bounded);
            case LANDLORD -> ownershipRepo.findPageByPropertyOwner(userId, propertyId, active, bounded);
            case SUPER_ADMIN -> ownershipRepo.findAllOwnershipViews(propertyId, active, bounded);
            default -> userDao.hasPermission(Permission.VIEW_ESTATE)
                    ? ownershipRepo.findPageByPropertyStaff(userId, propertyId, active, bounded)
                    : Page.empty(bounded);
        };
    }

    @Transactional
    public PropertyOwnership end(long id, OwnershipTerminationRequest request) {
        PropertyOwnership ownership = ownershipRepo.findById(id).filter(PropertyOwnership::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND));
        long actorId = userDao.getUserId();
        Property property = requireManagedProperty(ownership.getPropertyId(), actorId);
        if (request.endDate().isBefore(ownership.getOwnershipStart()) || request.endDate().isAfter(LocalDate.now())) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        ownership.setOwnershipEnd(request.endDate());
        ownership.setTerminationReason(request.reason().trim());
        ownership.setTerminatedBy(actorId);
        ownership.setTerminatedAt(java.time.ZonedDateTime.now(PMSUtils.getZoneId()));
        ownership.setActive(false);
        PropertyOwnership ended = ownershipRepo.save(ownership);
        notifyOwnershipEnded(ownership, property.getName(), request.endDate(), request.reason().trim());
        return ended;
    }

    private void notifyOwnershipEnded(PropertyOwnership ownership, String propertyName, LocalDate endDate, String reason) {
        userDao.findById(ownership.getHomeownerUserId()).filter(Users::isActive)
                .filter(homeowner -> homeowner.getEmail() != null && !homeowner.getEmail().isBlank())
                .ifPresent(homeowner -> {
                    String location = propertyName + (ownership.getUnitId() == null ? "" : " / unit " + ownership.getUnitId());
                    String body = String.format(i18n.getLocalizedMessage(NotificationType.OWNERSHIP_ENDED_EMAIL.getBody()),
                            homeowner.getFullName(), location, endDate, reason);
                    notificationService.queueNotification(new NotificationDTO(body, homeowner.getEmail(), NotificationType.OWNERSHIP_ENDED_EMAIL));
                });
    }

    @Transactional
    public PropertyOwnership transferFromSale(long propertyId, Long unitId, long buyerId, long saleId) {
        var completedTransfer = ownershipRepo.findBySourceSaleTransactionId(saleId);
        if (completedTransfer.isPresent()) {
            PropertyOwnership ownership = completedTransfer.get();
            if (ownership.getPropertyId() != propertyId || !java.util.Objects.equals(ownership.getUnitId(), unitId)
                    || ownership.getHomeownerUserId() != buyerId) {
                throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION);
            }
            return ownership;
        }
        Users buyer = userDao.findById(buyerId).filter(Users::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.LOAD_USER_ERROR));
        if (unitId == null) throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        Unit unit = unitRepo.findAndLockById(unitId)
                .filter(candidate -> candidate.isActive() && candidate.getPropertyId() == propertyId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        ownershipRepo.findFirstByUnitIdAndActiveTrue(unitId)
                .filter(existing -> existing.getHomeownerUserId() == buyerId)
                .ifPresent(existing -> { throw new PMSCustomException(ResponseCode.DATA_INTEGRITY_VIOLATION); });
        PropertyOwnership ownership = createUnitOwnership(unit, buyer.getId(), LocalDate.now(),
                "SALE_COMPLETION", userDao.getUserId());
        ownership.setSourceSaleTransactionId(saleId);
        return ownershipRepo.save(ownership);
    }

    @Transactional
    public EstateServiceCharge createServiceCharge(ServiceChargeRequest request) {
        PropertyOwnership ownership = ownershipRepo.findById(request.ownershipId()).filter(PropertyOwnership::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND));
        requireManagedProperty(ownership.getPropertyId(), userDao.getUserId());
        if (ownership.getUnitId() == null) throw new PMSCustomException(ResponseCode.UNIT_NOT_FOUND);
        Unit unit = unitRepo.findById(ownership.getUnitId()).filter(Unit::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.UNIT_NOT_FOUND));
        if (unit.getCurrency() == null || !unit.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        }
        PMSInvoice invoice = invoiceService.createPropertyInvoice(ownership.getUnitId(), ownership.getHomeownerUserId(),
                Map.of(request.description(), request.amount().doubleValue()), "SERVICE_CHARGE", request.dueDate());
        EstateServiceCharge charge = new EstateServiceCharge();
        charge.setPropertyId(ownership.getPropertyId()); charge.setUnitId(ownership.getUnitId());
        charge.setHomeownerUserId(ownership.getHomeownerUserId()); charge.setInvoiceId(invoice.getId());
        charge.setAmount(request.amount()); charge.setCurrency(request.currency().toUpperCase());
        charge.setDueDate(request.dueDate()); charge.setDescription(request.description());
        charge.setCreatedBy(userDao.getUserId()); charge.setActive(true);
        return chargeRepo.save(charge);
    }

    public Page<ServiceChargeView> listServiceCharges(Pageable pageable, Long propertyId) {
        long userId = userDao.getUserId();
        Pageable bounded = PageRequest.of(Math.max(0, pageable.getPageNumber()), Math.min(100, Math.max(1, pageable.getPageSize())));
        return switch (userDao.getActiveRole()) {
            case HOMEOWNER -> chargeRepo.findPageByHomeowner(userId, propertyId, bounded);
            case LANDLORD -> chargeRepo.findPageByPropertyOwner(userId, propertyId, bounded);
            case SUPER_ADMIN -> chargeRepo.findAllActive(propertyId, bounded);
            default -> userDao.hasPermission(Permission.VIEW_SERVICE_CHARGE)
                    ? chargeRepo.findPageByPropertyStaff(userId, propertyId, bounded)
                    : Page.empty(bounded);
        };
    }

    private Property requireManagedProperty(long propertyId, long userId) {
        PMSRole role = userDao.getActiveRole();
        return (role == PMSRole.SUPER_ADMIN
                ? propertyRepo.findById(propertyId).filter(Property::isActive)
                : role == PMSRole.LANDLORD
                    ? propertyRepo.findByIdAndCreatedByAndActiveTrue(propertyId, userId)
                    : userDao.hasPermission(Permission.MANAGE_ESTATE)
                        ? propertyRepo.findByIdAndStaffOrOwner(propertyId, userId)
                        : java.util.Optional.<Property>empty())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
    }
}
