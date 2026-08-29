package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.PaymentOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepo extends JpaRepository<PaymentOperation,Long>{
    Optional<PaymentOperation> findByIdempotencyKey(String key);
    List<PaymentOperation> findAllByCaseReferenceOrderByOccurredAtAsc(String caseReference);
    @Query("SELECT COALESCE(SUM(o.amount),0) FROM PaymentOperation o WHERE o.paymentId=:paymentId AND o.operationType=:type AND o.status='CONFIRMED'")
    BigDecimal sumConfirmed(long paymentId,String type);
    @Query("SELECT o FROM PaymentOperation o JOIN PMSInvoice i ON o.invoiceId=i.id WHERE o.caseReference=:caseReference AND " +
            "(:privileged=true OR i.billedUserId=:userId OR i.payToUserId=:userId OR i.propertyId IN " +
            "(SELECT pm.propertyId FROM PropertyManager pm WHERE pm.userId=:userId AND pm.active)) ORDER BY o.occurredAt ASC")
    List<PaymentOperation> findVisibleCase(String caseReference,long userId,boolean privileged);
}
