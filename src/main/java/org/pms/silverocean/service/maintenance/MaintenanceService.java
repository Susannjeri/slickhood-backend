package org.pms.silverocean.service.maintenance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.MaintenanceWorkOrderRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.MaintenanceWorkOrder;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class MaintenanceService {
 private final MaintenanceWorkOrderRepo orders;private final UnitRepo units;private final UserDao users;
 @Transactional public MaintenanceModels.View create(MaintenanceModels.Create request){long userId=users.getUserId();units.findByIdAndStaffOrOwnerOrTenant(request.unitId(),userId).orElseThrow(()->new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS));Unit unit=units.findById(request.unitId()).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));MaintenanceWorkOrder w=new MaintenanceWorkOrder();w.setWorkOrderNumber("WO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());w.setPropertyId(unit.getPropertyId());w.setUnitId(unit.getId());w.setRequestedByUserId(userId);w.setCreatedBy(userId);w.setTitle(request.title().trim());w.setDescription(request.description().trim());w.setCategory(request.category().name());w.setPriority(request.priority().name());w.setStatus(MaintenanceModels.Status.OPEN.name());w.setCurrency(unit.getCurrency());w.setActive(true);return new MaintenanceModels.View(orders.save(w));}
 @Transactional(readOnly=true) public List<MaintenanceModels.View> list(long unitId){return orders.findAccessibleByUnit(unitId,users.getUserId()).stream().map(MaintenanceModels.View::new).toList();}
 @Transactional public MaintenanceModels.View update(long id,MaintenanceModels.Update request){long userId=users.getUserId();MaintenanceWorkOrder w=orders.findAccessible(id,userId).orElseThrow(()->new PMSCustomException(ResponseCode.RESOURCE_NOT_FOUND));boolean staff=units.findByIdAndStaffOrOwner(w.getUnitId(),userId).isPresent();if(!staff&&request.status()!=MaintenanceModels.Status.CANCELLED)throw new PMSCustomException(ResponseCode.FORBIDDEN_ACCESS);MaintenanceModels.Status current=MaintenanceModels.Status.valueOf(w.getStatus());if(!allowed(current,request.status()))throw new PMSCustomException(ResponseCode.INVALID_FIELD_DATA);w.setStatus(request.status().name());w.setAssignedProviderServiceId(request.assignedProviderServiceId());w.setScheduledAt(request.scheduledAt());w.setEstimatedCost(request.estimatedCost());w.setActualCost(request.actualCost());if(StringUtils.isNotBlank(request.currency()))w.setCurrency(request.currency().trim().toUpperCase());w.setResolutionNotes(StringUtils.trimToNull(request.resolutionNotes()));if(request.status()==MaintenanceModels.Status.COMPLETED)w.setCompletedAt(ZonedDateTime.now(PMSUtils.getZoneId()));return new MaintenanceModels.View(orders.save(w));}
 private boolean allowed(MaintenanceModels.Status from,MaintenanceModels.Status to){if(from==to)return true;return switch(from){case OPEN->Set.of(MaintenanceModels.Status.ACKNOWLEDGED,MaintenanceModels.Status.CANCELLED).contains(to);case ACKNOWLEDGED->Set.of(MaintenanceModels.Status.IN_PROGRESS,MaintenanceModels.Status.CANCELLED).contains(to);case IN_PROGRESS->Set.of(MaintenanceModels.Status.COMPLETED,MaintenanceModels.Status.CANCELLED).contains(to);case COMPLETED,CANCELLED->false;};}
}
