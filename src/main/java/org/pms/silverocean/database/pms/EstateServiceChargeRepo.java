package org.pms.silverocean.database.pms;

import org.pms.silverocean.database.pms.entities.EstateServiceCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.time.ZonedDateTime;

public interface EstateServiceChargeRepo extends JpaRepository<EstateServiceCharge,Long>{
 List<EstateServiceCharge> findAllByHomeownerUserIdAndActiveTrueOrderByDueDateDesc(long userId);
 @Query("SELECT c FROM EstateServiceCharge c JOIN PropertyManager pm ON pm.propertyId=c.propertyId WHERE c.active AND pm.active AND pm.userId=:userId AND pm.roleName=:roleName ORDER BY c.dueDate DESC")
 List<EstateServiceCharge> findAllByManager(long userId,String roleName);
 @Query("SELECT COUNT(c) FROM EstateServiceCharge c JOIN PMSInvoice i ON i.id=c.invoiceId WHERE c.homeownerUserId=:userId AND c.active AND i.active AND i.paid=false")
 long countOutstandingByHomeowner(long userId);
 @Query("SELECT COUNT(c) FROM EstateServiceCharge c JOIN PMSInvoice i ON i.id=c.invoiceId JOIN PropertyManager pm ON pm.propertyId=c.propertyId WHERE pm.userId=:userId AND pm.roleName=:roleName AND pm.active AND c.active AND i.active AND i.paid=false")
 long countOutstandingByManager(long userId,String roleName);
 @Query("SELECT DISTINCT c FROM EstateServiceCharge c JOIN Property p ON p.id=c.propertyId WHERE c.active AND c.createdOn >= :start AND c.createdOn < :end AND " +
         "(:privileged=true OR c.homeownerUserId=:userId OR p.createdBy=:userId OR EXISTS " +
         "(SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=c.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY c.dueDate DESC")
 List<EstateServiceCharge> findForReport(long userId,boolean privileged,ZonedDateTime start,ZonedDateTime end);
}
