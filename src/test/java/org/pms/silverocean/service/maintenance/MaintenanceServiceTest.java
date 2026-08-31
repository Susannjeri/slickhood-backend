package org.pms.silverocean.service.maintenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.MaintenanceWorkOrderRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.MaintenanceWorkOrder;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.property.wrappers.DbUnitDTO;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest{
 @Mock MaintenanceWorkOrderRepo orders;@Mock UnitRepo units;@Mock UserDao users;
 @Test void staffCanCompleteWorkOrder(){MaintenanceService service=new MaintenanceService(orders,units,users);MaintenanceWorkOrder w=order("IN_PROGRESS");when(users.getUserId()).thenReturn(7L);when(orders.findAccessible(1,7)).thenReturn(Optional.of(w));when(units.findByIdAndStaffOrOwner(5L,7)).thenReturn(Optional.of(new Unit()));when(orders.save(w)).thenReturn(w);var result=service.update(1,new MaintenanceModels.Update(MaintenanceModels.Status.COMPLETED,null,null,null,null,"KES","Fixed"));assertEquals("COMPLETED",result.status());assertNotNull(result.completedAt());}
 @Test void finalWorkOrderCannotBeReopened(){MaintenanceService service=new MaintenanceService(orders,units,users);MaintenanceWorkOrder w=order("COMPLETED");when(users.getUserId()).thenReturn(7L);when(orders.findAccessible(1,7)).thenReturn(Optional.of(w));when(units.findByIdAndStaffOrOwner(5L,7)).thenReturn(Optional.of(new Unit()));assertThrows(PMSCustomException.class,()->service.update(1,new MaintenanceModels.Update(MaintenanceModels.Status.OPEN,null,null,null,null,null,null)));}
 @Test void ownershipScopedUserCanCreateAWorkOrder(){MaintenanceService service=new MaintenanceService(orders,units,users);Unit unit=new Unit();unit.setId(5L);unit.setPropertyId(2L);unit.setCurrency("KES");unit.setActive(true);when(users.getUserId()).thenReturn(9L);when(units.findByIdAndStaffOrOwnerOrTenant(5L,9L)).thenReturn(Optional.of(mock(DbUnitDTO.class)));when(units.findById(5L)).thenReturn(Optional.of(unit));when(orders.save(any())).thenAnswer(invocation->invocation.getArgument(0));var result=service.create(new MaintenanceModels.Create(5L,"Kitchen leak","Water under the sink",MaintenanceModels.Category.PLUMBING,MaintenanceModels.Priority.HIGH));assertEquals(9L,result.requestedByUserId());assertEquals(2L,result.propertyId());assertEquals("OPEN",result.status());}
 @Test void homeownerCannotCancelAnotherUsersRequest(){MaintenanceService service=new MaintenanceService(orders,units,users);MaintenanceWorkOrder w=order("OPEN");when(users.getUserId()).thenReturn(7L);when(orders.findAccessible(1,7)).thenReturn(Optional.of(w));when(units.findByIdAndStaffOrOwner(5L,7)).thenReturn(Optional.empty());assertThrows(PMSCustomException.class,()->service.update(1,new MaintenanceModels.Update(MaintenanceModels.Status.CANCELLED,null,null,null,null,null,null)));verify(orders,never()).save(any());}
 private MaintenanceWorkOrder order(String status){MaintenanceWorkOrder w=new MaintenanceWorkOrder();w.setId(1L);w.setUnitId(5);w.setPropertyId(2);w.setRequestedByUserId(9);w.setWorkOrderNumber("WO-1");w.setTitle("Leak");w.setDescription("Leak");w.setCategory("PLUMBING");w.setPriority("HIGH");w.setStatus(status);w.setActive(true);return w;}
}
