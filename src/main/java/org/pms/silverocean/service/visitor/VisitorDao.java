package org.pms.silverocean.service.visitor;

import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.VisitorRepo;
import org.pms.silverocean.database.pms.entities.Visitor;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VisitorDao {

    private final VisitorRepo visitorRepo;
    private final AuditLogService auditLogService;
    private final UnitRepo unitRepo;

    public VisitorDao(VisitorRepo visitorRepo, AuditLogService auditLogService, UnitRepo unitRepo) {
        this.visitorRepo = visitorRepo;
        this.auditLogService = auditLogService;
        this.unitRepo = unitRepo;
    }

    public void save(Visitor visitor, String auditAction) {
        visitor.setLastModifiedDate(LocalDateTime.now());
        visitorRepo.save(visitor);

        auditLogService.createAuditLog(visitor, auditAction);
    }

    public Optional<Visitor> findByIdAndGuard(long visitorId, long userId) {
        return visitorRepo.findByIdAndGuard(visitorId, userId, PMSRole.GUARD.name());
    }

    public Optional<Visitor> findByIdAndCreatedBy(long visitorId, long createdBy) {
        return visitorRepo.findByIdAndCreatedBy(visitorId, createdBy);
    }

    public List<Visitor> findByTenantOrGuardOrLandlordOrPropertyManager(Pageable pageable, long userId) {
        return visitorRepo.findByTenantOrLandlordOrGuardOrPropertyManager(pageable.getPageSize(), pageable.getOffset(), userId);
    }

    public List<Visitor> findByTenantOrGuardOrLandlordOrPropertyManagerAndPhoneNumber(Pageable pageable, long userId, String phoneNumber) {
        return visitorRepo.findByTenantOrLandlordOrGuardOrPropertyManagerAndPhoneNumber(pageable.getPageSize(), pageable.getOffset(), userId, phoneNumber);
    }

    public Page<Visitor> findByUnitId(Pageable pageable, long unitId) {
        return visitorRepo.findByUnitId(pageable, unitId);
    }

    public Page<Visitor> findByUnitIdAndStatus(Pageable pageable, long unitId, String status) {
        return visitorRepo.findByUnitIdAndStatus(pageable, unitId, status);
    }

    public Optional<Visitor> findByUnitIdAndVisitorPhoneNumberAndVisitingTime(long unitId, String visitorPhoneNumber, ZonedDateTime expectedArrivalTime) {
        return visitorRepo.findByUnitIdAndVisitorPhoneNumberAndExpectedArrivalTime(unitId, visitorPhoneNumber, expectedArrivalTime);
    }

    public Optional<PropertyIdUnitRefPropertyNameProjection> checkIfTenantInUnit(long unitId, long userId) {
        return unitRepo.getByUnitIdAndUserIdIsTenant(unitId, userId);
    }

    public boolean canStaffOrOwnerAccessUnit(long unitId, long userId) {
        return unitRepo.findByIdAndStaffOrOwner(unitId, userId).isPresent();
    }

    public List<Visitor> findExpiredPendingVisitors(ZonedDateTime cutoff, int batchSize) {
        return visitorRepo.findExpiredPendingVisitors(cutoff, PageRequest.of(0, batchSize));
    }
}
