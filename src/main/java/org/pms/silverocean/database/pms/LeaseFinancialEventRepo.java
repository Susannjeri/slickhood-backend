package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.LeaseFinancialEvent;import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Query;import java.math.BigDecimal;import java.util.List;import java.util.Optional;
public interface LeaseFinancialEventRepo extends JpaRepository<LeaseFinancialEvent,Long>{
 Optional<LeaseFinancialEvent> findByIdempotencyKey(String key);List<LeaseFinancialEvent> findAllByLeaseIdOrderByOccurredAtAsc(long leaseId);
 boolean existsByLeaseIdAndInvoiceIdAndEventType(long leaseId,long invoiceId,String eventType);
 @Query("SELECT COALESCE(SUM(e.amount),0) FROM LeaseFinancialEvent e WHERE e.leaseId=:leaseId AND e.eventType=:type")BigDecimal total(long leaseId,String type);
}
