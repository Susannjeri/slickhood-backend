package org.pms.silverocean.service.lease;

import jakarta.persistence.EntityManager;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.InviteRepo;
import org.pms.silverocean.database.pms.LeaseChargeRepo;
import org.pms.silverocean.database.pms.LeaseRepo;
import org.pms.silverocean.database.pms.UnitTenantRepo;
import org.pms.silverocean.database.pms.entities.Lease;
import org.pms.silverocean.database.pms.entities.LeaseCharge;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.UnitTenant;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.invites.InviteType;
import org.pms.silverocean.service.lease.wrappers.LeaseContextDTO;
import org.pms.silverocean.service.lease.wrappers.LeaseDTO;
import org.pms.silverocean.service.lease.wrappers.TenancyProjection;
import org.pms.silverocean.service.payment.invoice.wrappers.ProcessLeaseInvoiceDTO;
import org.pms.silverocean.service.property.wrappers.UnitChargeProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LeaseDao {
    private final InviteRepo inviteRepo;

    private final UnitTenantRepo unitTenantRepo;
    private final LeaseRepo leaseRepo;
    private final AuditLogService auditLogService;
    private final LeaseChargeRepo leaseChargeRepo;
    private final EntityManager entityManager;

    private final Set<Lease> leaseCache = Collections.synchronizedSet(new HashSet<>());

    public LeaseDao(InviteRepo inviteRepo, UnitTenantRepo unitTenantRepo, LeaseRepo leaseRepo, AuditLogService auditLogService, LeaseChargeRepo leaseChargeRepo, EntityManager entityManager) {
        this.inviteRepo = inviteRepo;
        this.unitTenantRepo = unitTenantRepo;
        this.leaseRepo = leaseRepo;
        this.auditLogService = auditLogService;
        this.leaseChargeRepo = leaseChargeRepo;
        this.entityManager = entityManager;
    }

    public void createLease(Lease lease) {
        if (lease.getId() == null) {
            lease.setLastModifiedDate(LocalDateTime.now());
            leaseRepo.save(lease);
            auditLogService.createAuditLog(lease, Permission.CREATE_LEASE);
        } else {
            auditLogService.createAuditLog(lease, Permission.CREATE_LEASE, "Create lease failed, use of save method to update lease", false);
            throw new PMSCustomException(ResponseCode.LEASE_ALREADY_EXISTS);
        }
    }

    public void saveUnitTenant(UnitTenant unitTenant) {
        unitTenantRepo.save(unitTenant);
    }

    public void saveLease(Lease lease, String action) {
        lease.setLastModifiedDate(LocalDateTime.now());
        leaseRepo.save(lease);
        auditLogService.createAuditLog(lease, action);
    }

    public void incrementNextPaymentDate(Set<Long> idsToIncrement) {
        leaseRepo.incrementNextPaymentDate(idsToIncrement);
    }

    public void deactivatePaymentDue(Set<Long> idsToDeactivate) {
        leaseRepo.deactivatePaymentDue(idsToDeactivate);
    }

    public void clearEntityManagerPaginationCache() {
        entityManager.clear();
    }

    public void updateLeaseChargeNextPaymentDate(long leaseChargeId, LocalDate nextPaymentDate) {
        leaseChargeRepo.updateNextPaymentDate(leaseChargeId, nextPaymentDate);
    }

    public Optional<Lease> getLeaseFromTokenAndUser(String token, long userId) {
        Optional<Unit> unitContainer = inviteRepo.findUnitFromToken(token, InviteType.TENANT.name());
        if (unitContainer.isEmpty()) {
            return Optional.empty();
        }
        return inviteRepo.findByUnitIdAndTenant(unitContainer.get().getId(), userId);
    }

    public void deleteUnsignedLeaseAndUnitTenantsByUnitIdAndLeaseId(long unitId, long leaseId) {
        leaseRepo.deactivateUnsignedActiveLeaseIdByUnitId(unitId, leaseId);
        unitTenantRepo.deactivateUnsignedUnitTenantsIdByUnitId(unitId, leaseId);
        inviteRepo.deactivateInviteByUnitId(unitId, InviteType.TENANT.name());
    }

    public Optional<Lease> getLeaseByIdAndStaffOwnerOrTenantId(long leaseId, long userId) {
        return leaseRepo.findByIdAndTenantOrStaff(leaseId, userId);
    }

    public Optional<Lease> getLeaseById(long leaseId) { return leaseRepo.findById(leaseId); }

    public Optional<LeaseContextDTO> getContextToPrepLeaseMessage(long leaseId, long userId) {
        return leaseRepo.getLeaseProcessingContext(leaseId, userId);
    }

    public Optional<UnitTenant> getUnitTenantByTenantId(long tenantId) {
        return unitTenantRepo.findByIdAndActiveTrue(tenantId);
    }

    public Optional<Unit> getUnitByTenantId(long tenantId) {
        return unitTenantRepo.findUnitByTenantId(tenantId);
    }

    public Optional<Lease> getLeaseByIdAndStaffOwner(long leaseId, long userId) {
        return leaseRepo.findByIdAndStaff(leaseId, userId);
    }

    public Optional<Lease> getLeaseByIdAndOwner(long leaseId, long userId) {
        return leaseRepo.findByIdAndOwner(leaseId, userId);
    }

    public Optional<Lease> getLeaseByIdAndManagerRole(long leaseId, long userId, String roleName) {
        return leaseRepo.findByIdAndManagerRole(leaseId, userId, roleName);
    }

    public Optional<Lease> getLeaseByIdAndTenantId(long leaseId, long userId) {
        return leaseRepo.findByIdAndTenant(leaseId, userId);
    }

    public List<TenancyProjection> getTenancyByUserId(long userId) {
        return unitTenantRepo.findByUserIdAndActiveTrue(userId);
    }

    public void saveAllLeaseCharges(List<LeaseCharge> leaseCharges) {
        leaseChargeRepo.saveAll(leaseCharges);
    }

    public List<UnitChargeProjection> getLeaseChargeByLeaseId(long leaseId) {
        return leaseChargeRepo.findByLeaseId(leaseId);
    }

    public void updateSignedLeaseCharges(long leaseId) {
        leaseChargeRepo.updateSignedLeaseChargeNextPaymentDate(leaseId);
    }

    public Page<LeaseDTO> getLeaseList(long userId, boolean privileged, Pageable pageable) {
        return leaseRepo.findAccessibleLeases(userId, privileged, pageable);
    }

    public List<Lease> getTerminationCandidates(LocalDate today, Pageable pageable) {
        return leaseRepo.findTerminationCandidates(today, pageable);
    }

    public List<Lease> getExpiryCandidates(LocalDate today, Pageable pageable) {
        return leaseRepo.findExpiryCandidates(today, pageable);
    }

    public Slice<ProcessLeaseInvoiceDTO> getLeasePaymentsDueToday(Pageable pageable) {
        return leaseRepo.findLeasePaymentsDueToday(pageable);
    }
}
