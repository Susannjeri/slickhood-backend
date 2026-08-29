package org.pms.silverocean.service.property;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.BulkUnitJobRepo;
import org.pms.silverocean.database.pms.ChargeTypeRepo;
import org.pms.silverocean.database.pms.InviteRepo;
import org.pms.silverocean.database.pms.UnitChargeRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.UnitTenantRepo;
import org.pms.silverocean.database.pms.UtilitiesRepo;
import org.pms.silverocean.database.pms.entities.BulkUnitJob;
import org.pms.silverocean.database.pms.entities.ChargeType;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitCharge;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.database.pms.entities.Utility;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.lease.wrappers.LeaseIdTenantSignDateDTO;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import org.pms.silverocean.service.property.wrappers.PropertyNameAddressAndTypeProjection;
import org.pms.silverocean.service.property.wrappers.TenantNameEmailPhoneAndUnitRefProjection;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.pms.silverocean.service.property.wrappers.UnitTenantProjection;
import org.pms.silverocean.service.visitor.projections.PropertyIdUnitRefPropertyNameProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.pms.silverocean.service.property.UnitSpecification.createGetUnitSpecification;

@Service
public class UnitDao {
    private final UnitRepo unitRepo;
    private final UnitTenantRepo unitTenantRepo;

    private final BulkUnitJobRepo bulkUnitJobRepo;
    private final UtilitiesRepo utilitiesRepo;

    private final ChargeTypeRepo chargeTypeRepo;
    private final UnitChargeRepo unitChargeRepo;

    private final AuditLogService auditLogService;

    private final InviteRepo inviteRepo;

    private final LoadingCache<Long, Optional<Utility>> utilitiesCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(24)).build(new CacheLoader<>() {
                @Override
                public Optional<Utility> load(Long id) {
                    try {
                        return utilitiesRepo.findById(id);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
            });

    private final LoadingCache<Long, Optional<ChargeType>> chargeTypeCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(24)).build(new CacheLoader<>() {
                @Override
                public Optional<ChargeType> load(Long id) {
                    try {
                        return chargeTypeRepo.findById(id);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
            });



    public UnitDao(UnitRepo unitRepo, UnitTenantRepo unitTenantRepo, BulkUnitJobRepo bulkUnitJobRepo, UtilitiesRepo utilitiesRepo, ChargeTypeRepo chargeTypeRepo, UnitChargeRepo unitChargeRepo, AuditLogService auditLogService, InviteRepo inviteRepo) {
        this.unitRepo = unitRepo;
        this.unitTenantRepo = unitTenantRepo;
        this.bulkUnitJobRepo = bulkUnitJobRepo;
        this.utilitiesRepo = utilitiesRepo;
        this.chargeTypeRepo = chargeTypeRepo;
        this.unitChargeRepo = unitChargeRepo;
        this.auditLogService = auditLogService;
        this.inviteRepo = inviteRepo;
    }

    public void save(Unit unit) throws PMSCustomException {
        if (unit.getId() == null || findById(unit.getId()).isEmpty()) {
            if (unit.getCreatedBy() == 0) {
                throw new PMSCustomException(ResponseCode.CREATOR_MUST_BE_SET);
            }
            Optional<Unit> optional = unitRepo.findByRefAndPropertyId(unit.getRef(), unit.getPropertyId());
            if (optional.isPresent()) {
                auditLogService.createAuditLog(unit, Permission.CREATE_UNIT, "Create new unit failed, duplicate unit ref", false);
                throw new PMSCustomException(ResponseCode.UNIT_CREATION_FAILED_DUPLICATE);
            }
            unit.setLastModifiedDate(LocalDateTime.now());
            auditLogService.createAuditLog(unit, Permission.CREATE_UNIT);
            unitRepo.save(unit);
        } else {
            auditLogService.createAuditLog(unit, Permission.CREATE_UNIT, "Create new unit failed, use of save method for update", false);
            throw new PMSCustomException(ResponseCode.UNIT_CREATION_FAILED_DUPLICATE);
        }
    }

    @Transactional
    public void batchSave(Set<Unit> units, Unit unitFromDb, BulkUnitJob bulkUnitJob) throws PMSCustomException {
        auditLogService.createAuditLog(unitFromDb, Permission.DUPLICATE_UNIT, "Creating bulk units from base Unit", true);
        unitRepo.saveAll(units);
        bulkUnitJob.setLastModifiedDate(LocalDateTime.now());
        bulkUnitJob.setCompleted(true);
        bulkUnitJobRepo.save(bulkUnitJob);
        unitRepo.flush();
    }

    public void batchUpdate(Set<Unit> units) {
        unitRepo.saveAll(units);
    }

    public void update(Unit unit) {
        Optional<Unit> unitFromDb = unitRepo.findByRefAndPropertyId(unit.getRef(), unit.getPropertyId());
        if (unitFromDb.isPresent() && !unitFromDb.get().getId().equals(unit.getId())) {
            auditLogService.createAuditLog(unit, Permission.EDIT_UNIT, "Duplicate unit ref. Ref must be unique in the same property", false);
            throw new PMSCustomException(ResponseCode.UNIT_EDIT_FAILED_DUPLICATE_DETAILS);
        }
        unit.setLastModifiedDate(LocalDateTime.now());
        unitRepo.save(unit);
        auditLogService.createAuditLog(unit, Permission.EDIT_UNIT);
    }

//    public void updateUnitOccupancy(Unit unit) {
//        unit.setLastModifiedDate(LocalDateTime.now());
//        unitRepo.save(unit);
//        auditLogService.createAuditLog(unit, unit.isOccupied() ? "lease_started" : "lease_ended");
//    }

    public void delete(Unit unit) {
        unit.setActive(false);
        unit.setLastModifiedDate(LocalDateTime.now());
        unitRepo.save(unit);
        auditLogService.createAuditLog(unit, Permission.DELETE_UNIT, "User initiated delete unit action", true);
    }

    @Transactional
    public void updateUnitCharges(long unitId, Set<UnitCharge> unitCharges) {
        unitChargeRepo.deleteByUnitId(unitId);
        unitChargeRepo.saveAll(unitCharges);
        unitCharges.forEach(unitCharge -> auditLogService.createAuditLog(unitCharge, Permission.EDIT_UNIT_CHARGES, "Updated Unit Charges", true));
    }

    public List<UnitChargeProjection> getUnitCharges(long unitId) {
        return unitChargeRepo.findByUnitId(unitId);
    }

    public List<UnitCharge> getAllUnitCharges(long unitId) {
        return unitChargeRepo.findAllByUnitId(unitId);
    }

    public void createBulkUnitJob(BulkUnitJob bulkUnitJob) {
        bulkUnitJob.setActive(true);
        bulkUnitJobRepo.save(bulkUnitJob);
        auditLogService.createAuditLog(bulkUnitJob, Permission.DUPLICATE_UNIT, "User submitted create similar units action", true);
    }

    public Page<BulkUnitJob> listBulkUnitJob(Pageable pageable, long createdBy) {
        return bulkUnitJobRepo.findByCreatedBy(pageable, createdBy);
    }

    public int countPendingBulkUnitJob(long createdBy) {
        Integer count = bulkUnitJobRepo.countBulkUnitJobByCreatedByAndActiveTrueAndCompletedFalse(createdBy);
        if (count == null) {
            return 0;
        }
        return count;
    }

    public void updateBulkUnitJob(BulkUnitJob bulkUnitJob) {
        bulkUnitJobRepo.save(bulkUnitJob);
    }

    public Optional<BulkUnitJob> findActiveJobById(long id) {
        return bulkUnitJobRepo.findByIdAndActiveTrueAndCompletedFalse(id);
    }

    public Set<BulkUnitJob> findIncompleteJobs() {
        return bulkUnitJobRepo.findIncompleteJobs();
    }

    public void logDeleteUnitFailure(Unit unit, String failureDescription) {
        auditLogService.createAuditLog(unit, Permission.DELETE_UNIT, failureDescription, false);
    }

    public Integer countActiveByPropertyId(Long propertyId) {
        return unitRepo.countAllByPropertyIdAndActiveTrue(propertyId);
    }

    public Optional<Unit> findById(long id) {
        return unitRepo.findById(id);
    }
    public Optional<Unit> findByAndLockById(long id) {
        return unitRepo.findAndLockById(id);
    }

    public Optional<Unit> findByToken(String token) {
        Optional<Unit> byTokenAndActiveTrue = unitRepo.findByTokenAndActiveTrue(token, InviteType.TENANT.name());
        if (byTokenAndActiveTrue.isPresent()) {
            inviteRepo.findByTokenAndActive(token, true).ifPresent(leaseInvite -> {
                leaseInvite.setLastModifiedDate(LocalDateTime.now());
                leaseInvite.setVisits(leaseInvite.getVisits() + 1);
                inviteRepo.save(leaseInvite);
            });
        }

        return byTokenAndActiveTrue;
    }

    public Optional<DbUnitDTO> findDTOByToken(String token) {
        Optional<DbUnitDTO> byTokenAndActiveTrue = unitRepo.findUnitDtoByTokenAndActiveTrue(token, InviteType.TENANT.name());
        if (byTokenAndActiveTrue.isPresent()) {
            inviteRepo.findByTokenAndActive(token, true).ifPresent(leaseInvite -> {
                leaseInvite.setLastModifiedDate(LocalDateTime.now());
                leaseInvite.setVisits(leaseInvite.getVisits() + 1);
                inviteRepo.save(leaseInvite);
            });
        }

        return byTokenAndActiveTrue;
    }

    public Page<UnitTenantProjection> findUnitTenantsByUnitIdAndOwnerOrPropertyManager(Pageable pageable, long unitId, long userId) {
        return unitRepo.findUnitTenantsByUnitIDAndUnitStaffAndActive(pageable, unitId, userId);
    }

    public Optional<LeaseIdTenantSignDateDTO> getSignedLeaseIdByUnitId(long unitId) {
        return unitTenantRepo.getActiveSignedLeaseIdByUnitId(unitId);
    }

    public Optional<LeaseIdTenantSignDateDTO> getLeaseIdByTenantsUserIdAndUnitId(long userId, long unitId) {
        return unitTenantRepo.getUnsignedActiveLeaseIdByTenantsUserIdAndUnitId(userId, unitId);
    }

    public List<Users> findPropertyManagersByUnit(long unitId) {
        return unitRepo.findPropertyManagerByUnitID(unitId, PMSRole.PROPERTY_MANAGER.name());
    }

    public Users getLandlordDetails(long unitId) {
        return unitRepo.getLandlordDetails(unitId);
    }

    public Optional<DbUnitDTO> findDTOByIdAndCreatedBy(Long id, long createdBy) {
        return unitRepo.findDTOByIdAndCreatedByAndActiveTrue(id, createdBy);
    }

    public Optional<Unit> findByIdAndCreatedBy(Long id, long createdBy) {
        return unitRepo.findByIdAndCreatedByAndActiveTrue(id, createdBy);
    }

    public Optional<Unit> findByIdAndStaffOrOwner(Long id, long userId) {
        return unitRepo.findByIdAndStaffOrOwner(id, userId);
    }

    public Optional<DbUnitDTO> findByIdAndStaffOrOwnerOrTenant(Long id, long userId) {
        return unitRepo.findByIdAndStaffOrOwnerOrTenant(id, userId);
    }

    public Optional<DbUnitDTO> findByIdAndTenant(Long id, long userId) {
        return unitRepo.findDTOByIdAndTenant(id, userId);
    }

    public Optional<DbUnitDTO> findByIdAndManagerRole(Long id, long userId, String roleName) {
        return unitRepo.findDTOByIdAndManagerRole(id, userId, roleName);
    }

    public Optional<DbUnitDTO> findByIdAndHomeowner(Long id, long userId) { return unitRepo.findDTOByIdAndHomeowner(id, userId); }
    public Optional<DbUnitDTO> findByIdAndBuyer(Long id, long userId) { return unitRepo.findDTOByIdAndBuyer(id, userId); }

    public List<PropertyIdUnitRefPropertyNameProjection> findByUserIDIsTenant(long userId) {
        return unitRepo.getAllByUnitIdAndUserIdIsTenant(userId);
    }

    public Page<Unit> findAll(Optional<String> ref, Optional<Long> propertyId, Optional<PMSLeaseMode> leaseMode, Long userId, PMSRole activeRole, Pageable pageable) {
        List<Specification<Unit>> specs = createGetUnitSpecification(ref, propertyId, userId, activeRole, leaseMode);
        return specs.isEmpty() ? unitRepo.findAll(pageable) : unitRepo.findAll(Specification.allOf(specs), pageable);
    }


    public List<Utility> getSupportedUtilities() {
        return utilitiesRepo.findAllByActiveTrue();
    }

    public Optional<Utility> getUtilities(long id) {
        return utilitiesCache.getUnchecked(id);
    }

    public void deactivateAllUnitsWithinProperty(long propertyId) {
        unitRepo.deactivateAllUnitsWithinProperty(propertyId);
    }

    public List<ChargeType> getSupportedChargeTypes() {
        return chargeTypeRepo.findAllByActiveTrue();
    }

    public Optional<PropertyNameAddressAndTypeProjection> getPropertyDetailsFromUnitId(Long unitId) {
        if (unitId == null) {
            return Optional.empty();
        }
        return unitRepo.getPropertyDetailsFromUnitId(unitId);
    }

    public Optional<TenantNameEmailPhoneAndUnitRefProjection> getTenantAndUnitDetailsByUnitId(long unitId, long tenantUserId) {
        return unitRepo.getTenantAndUnitDetailsByUnitId(unitId, tenantUserId);
    }
}
