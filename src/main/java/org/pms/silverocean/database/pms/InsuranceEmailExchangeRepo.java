package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.InsuranceEmailExchange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface InsuranceEmailExchangeRepo extends JpaRepository<InsuranceEmailExchange, Long> {
    boolean existsByCompanyIdAndExternalMessageId(long companyId, String externalMessageId);
    Optional<InsuranceEmailExchange> findByCorrelationId(String correlationId);
    List<InsuranceEmailExchange> findByCaseReferenceOrderByCreatedOnAsc(String caseReference);
    boolean existsByCaseReferenceAndCompanyIdAndMessageTypeAndDirectionAndStatusIn(
            String caseReference, long companyId, String messageType, String direction, Collection<String> statuses);
}
