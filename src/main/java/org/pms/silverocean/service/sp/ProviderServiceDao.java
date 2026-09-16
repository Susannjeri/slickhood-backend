package org.pms.silverocean.service.sp;

import org.pms.silverocean.database.pms.ProviderServiceRepo;
import org.pms.silverocean.database.pms.entities.ProviderService;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.sp.wrappers.ProviderServiceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;

@Service
public class ProviderServiceDao {
    private final ProviderServiceRepo repo;
    private final AuditLogService auditLogService;
    private final org.pms.silverocean.database.pms.ServiceTierRepo tiers;

    public ProviderServiceDao(ProviderServiceRepo repo, AuditLogService auditLogService, org.pms.silverocean.database.pms.ServiceTierRepo tiers) {
        this.repo = repo;
        this.auditLogService = auditLogService;
        this.tiers = tiers;
    }

    public void save(ProviderService service, String auditAction) {
        service.setLastModifiedDate(LocalDateTime.now());
        repo.save(service);
        auditLogService.createAuditLog(service, auditAction);
    }

    public Page<ProviderServiceDTO> findByProfileIdEnriched(long profileId, Pageable pageable) {
        return repo.findByProfileIdEnriched(profileId, pageable);
    }

    public Page<ProviderServiceDTO> findPendingAdminReviewEnriched(Pageable pageable) {
        return repo.findPendingAdminReviewEnriched(pageable);
    }

    public Page<ProviderServiceDTO> findListedByCategoryEnriched(Pageable pageable, Long categoryId) {
        return repo.findListedByCategoryEnriched(pageable, categoryId);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount,
                                                     Double latitude, Double longitude, Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return repo.searchDirectory(pageable, categoryId, query, minAmount, maxAmount, latitude, longitude, minLat, maxLat, minLng, maxLng);
    }

    public Page<ProviderServiceDTO> searchDirectory(Pageable pageable, Long categoryId, String query,
                                                     BigDecimal minAmount, BigDecimal maxAmount) {
        return searchDirectory(pageable,categoryId,query,minAmount,maxAmount,null,null,null,null,null,null);
    }

    public Optional<ProviderServiceDTO> findByIdEnriched(long id) {
        return repo.findByIdEnriched(id);
    }

    public Optional<ProviderService> findById(long id) {
        return repo.findById(id);
    }

    public String requireActiveTier(String name) {
        return tiers.findByNameIgnoreCaseAndActiveTrue(name.trim()).map(org.pms.silverocean.database.pms.entities.ServiceTier::getName)
                .orElseThrow(() -> new org.pms.silverocean.service.PMSCustomException(org.pms.silverocean.common.ResponseCode.INVALID_FIELD_DATA));
    }
    public Optional<ProviderService> findByIdForUpdate(long id){return repo.findByIdForUpdate(id);}
    public Optional<ProviderService> findOwnedForUpdate(long id,long profileId){return repo.findOwnedForUpdate(id,profileId);}

    public List<ProviderService> findAllById(Iterable<Long> ids) { return repo.findAllById(ids); }

    public Optional<ProviderService> findByIdAndProfileId(long id, long profileId) {
        return repo.findByIdAndProfileId(id, profileId);
    }
}
