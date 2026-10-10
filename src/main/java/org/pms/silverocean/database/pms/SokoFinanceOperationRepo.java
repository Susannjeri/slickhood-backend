package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SokoFinanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SokoFinanceOperationRepo extends JpaRepository<SokoFinanceOperation,Long> {
    Optional<SokoFinanceOperation> findByOrderIdAndProviderReference(long orderId,String providerReference);
}
