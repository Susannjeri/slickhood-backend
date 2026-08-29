package org.pms.silverocean.database.pms;
import org.pms.silverocean.database.pms.entities.MaintenanceWorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import java.time.ZonedDateTime;
public interface MaintenanceWorkOrderRepo extends JpaRepository<MaintenanceWorkOrder,Long>{
 @Query("SELECT w FROM MaintenanceWorkOrder w JOIN Unit u ON u.id=w.unitId WHERE w.active AND w.createdOn>=:start AND w.createdOn<:end AND (:privileged=true OR w.requestedByUserId=:userId OR u.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=u.propertyId AND pm.userId=:userId AND pm.active)) ORDER BY w.createdOn DESC")
 List<MaintenanceWorkOrder> findForReport(long userId,boolean privileged,ZonedDateTime start,ZonedDateTime end,Pageable pageable);
 @Query("SELECT w FROM MaintenanceWorkOrder w JOIN Unit u ON u.id=w.unitId WHERE w.active AND w.unitId=:unitId AND (w.requestedByUserId=:userId OR u.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=u.propertyId AND pm.userId=:userId AND pm.active) OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=u.propertyId AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active)) ORDER BY w.createdOn DESC")
 List<MaintenanceWorkOrder> findAccessibleByUnit(long unitId,long userId);
 @Query("SELECT w FROM MaintenanceWorkOrder w JOIN Unit u ON u.id=w.unitId WHERE w.active AND w.id=:id AND (w.requestedByUserId=:userId OR u.createdBy=:userId OR EXISTS (SELECT 1 FROM PropertyManager pm WHERE pm.propertyId=u.propertyId AND pm.userId=:userId AND pm.active) OR EXISTS (SELECT 1 FROM PropertyOwnership po WHERE po.propertyId=u.propertyId AND (po.unitId IS NULL OR po.unitId=u.id) AND po.homeownerUserId=:userId AND po.active))")
 Optional<MaintenanceWorkOrder> findAccessible(long id,long userId);
}
