package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.TaxConnectionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxConnectionRequestRepo extends JpaRepository<TaxConnectionRequest, Long> {
    List<TaxConnectionRequest> findByOwnerUserIdAndActiveTrueOrderByCreatedOnDesc(long ownerUserId);
    boolean existsByOwnerUserIdAndProviderAndActiveTrue(long ownerUserId, String provider);
    Page<TaxConnectionRequest> findAllByOrderByCreatedOnDesc(Pageable pageable);
}
