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
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest{
 @Mock MaintenanceWorkOrderRepo orders;@Mock UnitRepo units;@Mock UserDao users;
 @Test void staffCanCompleteWorkOrder(){MaintenanceService service=new MaintenanceService(orders,units,users);MaintenanceWorkOrder w=order("IN_PROGRESS");when(users.getUserId()).thenReturn(7L);when(orders.findAccessible(1,7)).thenReturn(Optional.of(w));when(units.findByIdAndStaffOrOwner(5L,7)).thenReturn(Optional.of(new Unit()));when(orders.save(w)).thenReturn(w);var result=service.update(1,new MaintenanceModels.Update(MaintenanceModels.Status.COMPLETED,null,null,null,null,"KES","Fixed"));assertEquals("COMPLETED",result.status());assertNotNull(result.completedAt());}
 @Test void finalWorkOrderCannotBeReopened(){MaintenanceService service=new MaintenanceService(orders,units,users);MaintenanceWorkOrder w=order("COMPLETED");when(users.getUserId()).thenReturn(7L);when(orders.findAccessible(1,7)).thenReturn(Optional.of(w));when(units.findByIdAndStaffOrOwner(5L,7)).thenReturn(Optional.of(new Unit()));assertThrows(PMSCustomException.class,()->service.update(1,new MaintenanceModels.Update(MaintenanceModels.Status.OPEN,null,null,null,null,null,null)));}
 private MaintenanceWorkOrder order(String status){MaintenanceWorkOrder w=new MaintenanceWorkOrder();w.setId(1L);w.setUnitId(5);w.setPropertyId(2);w.setRequestedByUserId(9);w.setWorkOrderNumber("WO-1");w.setTitle("Leak");w.setDescription("Leak");w.setCategory("PLUMBING");w.setPriority("HIGH");w.setStatus(status);w.setActive(true);return w;}
}
