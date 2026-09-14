package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.KycMatrixRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycMatrixRequirementRepo extends JpaRepository<KycMatrixRequirement, Long> {
    List<KycMatrixRequirement> findAllByReleaseIdOrderByScopeTypeAscScopeLabelAscRequirementLabelAsc(long releaseId);
    List<KycMatrixRequirement> findAllByReleaseIdAndActiveTrueAndScopeTypeAndScopeKeyOrderByRequirementLabel(long releaseId, String scopeType, String scopeKey);
    Optional<KycMatrixRequirement> findByIdAndReleaseId(long id, long releaseId);
}
