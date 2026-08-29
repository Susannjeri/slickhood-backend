package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ServiceTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceTierRepo extends JpaRepository<ServiceTier, Long> {
    @Query("SELECT t FROM ServiceTier t WHERE t.active = true ORDER BY t.name")
    Page<ServiceTier> findAllActive(Pageable pageable);
}
