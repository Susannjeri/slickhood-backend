package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.InsuranceEmailExchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsuranceEmailExchangeRepo extends JpaRepository<InsuranceEmailExchange, Long> {
    Optional<InsuranceEmailExchange> findByCorrelationId(String correlationId);
    List<InsuranceEmailExchange> findByCaseReferenceOrderByCreatedOnAsc(String caseReference);
    long countByDirectionAndStatusAndActiveTrue(String direction, String status);
}
