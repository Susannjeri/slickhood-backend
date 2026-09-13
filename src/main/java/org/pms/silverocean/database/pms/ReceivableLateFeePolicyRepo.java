package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ReceivableLateFeePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceivableLateFeePolicyRepo extends JpaRepository<ReceivableLateFeePolicy, Long> {
    Optional<ReceivableLateFeePolicy> findByCreatedByAndBillingTypeAndActiveTrue(long createdBy, String billingType);
}
