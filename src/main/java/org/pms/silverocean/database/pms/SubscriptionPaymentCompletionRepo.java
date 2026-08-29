package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.SubscriptionPaymentCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentCompletionRepo extends JpaRepository<SubscriptionPaymentCompletion, Long> {
    boolean existsByInvoiceId(long invoiceId);
}
