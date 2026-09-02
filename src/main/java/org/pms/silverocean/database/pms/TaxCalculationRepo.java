package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.TaxCalculation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxCalculationRepo extends JpaRepository<TaxCalculation, Long> {
    Page<TaxCalculation> findByOwnerUserIdOrderByCreatedOnDesc(long ownerUserId, Pageable pageable);
}
