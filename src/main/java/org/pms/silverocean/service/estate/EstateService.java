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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class EstateService {
    private final PropertyOwnershipRepo ownershipRepo;
    private final PropertyRepo propertyRepo;
    private final UnitRepo unitRepo;
    private final UserDao userDao;
    private final EstateServiceChargeRepo chargeRepo;
    private final InvoiceService invoiceService;

    public EstateService(PropertyOwnershipRepo ownershipRepo, PropertyRepo propertyRepo, UnitRepo unitRepo, UserDao userDao,
                         EstateServiceChargeRepo chargeRepo, InvoiceService invoiceService) {
        this.ownershipRepo = ownershipRepo; this.propertyRepo = propertyRepo; this.unitRepo = unitRepo; this.userDao = userDao;
        this.chargeRepo = chargeRepo; this.invoiceService = invoiceService;
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
            ownershipRepo.save(existing);
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

    public List<PropertyOwnership> list() {
        long userId = userDao.getUserId();
        return switch (userDao.getActiveRole()) {
            case HOMEOWNER -> ownershipRepo.findAllByHomeownerUserIdOrderByCreatedOnDesc(userId);
            case LANDLORD -> ownershipRepo.findAllByPropertyOwner(userId);
            case ESTATE_MANAGER -> ownershipRepo.findAllByManager(userId, PMSRole.ESTATE_MANAGER.name());
            case SUPER_ADMIN -> ownershipRepo.findAll();
            default -> List.of();
        };
    }

    @Transactional
    public PropertyOwnership end(long id, LocalDate endDate) {
        PropertyOwnership ownership = ownershipRepo.findById(id).filter(PropertyOwnership::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.OWNERSHIP_NOT_FOUND));
        requireManagedProperty(ownership.getPropertyId(), userDao.getUserId());
        if (endDate.isBefore(ownership.getOwnershipStart())) throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);
        ownership.setOwnershipEnd(endDate); ownership.setActive(false); return ownershipRepo.save(ownership);
    }

    public PropertyOwnership transferFromSale(long propertyId, Long unitId, long buyerId, long saleId) {
        OwnershipRequest request = new OwnershipRequest(propertyId, unitId, buyerId, LocalDate.now(), "SALE_COMPLETION");
        PropertyOwnership ownership = create(request); ownership.setSourceSaleTransactionId(saleId); return ownershipRepo.save(ownership);
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

    public Page<ServiceChargeView> listServiceCharges(Pageable pageable) {
        long userId = userDao.getUserId();
        return switch (userDao.getActiveRole()) {
            case HOMEOWNER -> chargeRepo.findPageByHomeowner(userId, pageable);
            case ESTATE_MANAGER -> chargeRepo.findPageByManager(userId, PMSRole.ESTATE_MANAGER.name(), pageable);
            case SUPER_ADMIN -> chargeRepo.findAllActive(pageable);
            default -> Page.empty(pageable);
        };
    }

    private Property requireManagedProperty(long propertyId, long userId) {
        PMSRole role = userDao.getActiveRole();
        return (role == PMSRole.SUPER_ADMIN
                ? propertyRepo.findById(propertyId).filter(Property::isActive)
                : role == PMSRole.LANDLORD
                    ? propertyRepo.findByIdAndCreatedByAndActiveTrue(propertyId, userId)
                    : role == PMSRole.ESTATE_MANAGER
                        ? propertyRepo.findByIdAndManagerRole(propertyId, userId, role.name())
                        : java.util.Optional.<Property>empty())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));
    }
}
