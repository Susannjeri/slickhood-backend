package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.VisitorAccessEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VisitorAccessEventRepo extends JpaRepository<VisitorAccessEvent, Long> {
    Page<VisitorAccessEvent> findAllByPropertyIdOrderByOccurredAtDesc(long propertyId, Pageable pageable);
    Optional<VisitorAccessEvent> findByCorrelationId(String correlationId);
}
