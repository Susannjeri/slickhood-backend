package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.ServiceFinanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceFinanceOperationRepo extends JpaRepository<ServiceFinanceOperation, Long> {
    Optional<ServiceFinanceOperation> findByProviderUserIdAndPaymentChannelAndOperationTypeAndProviderReference(
            long providerUserId, String paymentChannel, String operationType, String providerReference);

    List<ServiceFinanceOperation> findAllByBookingIdOrderByCreatedOnAsc(long bookingId);
}
