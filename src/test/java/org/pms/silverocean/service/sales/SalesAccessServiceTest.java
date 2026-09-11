package org.pms.silverocean.service.sales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.*;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesAccessServiceTest {
 @Mock PropertyRepo properties; @Mock UserDao users; @Mock WorkspaceSelectionService workspaces;
 @InjectMocks SalesAccessService access;
 @Test void landlordCannotUseDormantSalesRole() {
  when(users.getActiveRole()).thenReturn(PMSRole.LANDLORD);when(users.hasPermission(Permission.MANAGE_SALE_PIPELINE)).thenReturn(true);
  assertThrows(PMSCustomException.class,()->access.require(1,Permission.MANAGE_SALE_PIPELINE));verifyNoInteractions(properties);
 }
 @Test void salesOwnerCanOperateSaleUnitsInsideAPropertyWithAnotherDefaultMode() {
  Property p=new Property();p.setManagementMode(PMSPropertyManagementMode.RENTAL);
  when(users.getActiveRole()).thenReturn(PMSRole.SALES_AGENT);when(users.getUserId()).thenReturn(10L);
  when(users.hasPermission(Permission.MANAGE_SALE_PIPELINE)).thenReturn(true);
  when(properties.findByIdAndCreatedByAndActiveTrue(1L,10L)).thenReturn(Optional.of(p));
  assertSame(p,access.require(1,Permission.MANAGE_SALE_PIPELINE));
 }
 @Test void staffRequiresExactSelectedAssignment() {
  when(users.getActiveRole()).thenReturn(PMSRole.SALES_COORDINATOR);when(users.getUserId()).thenReturn(10L);
  when(users.hasPermission(Permission.MANAGE_SALE_PIPELINE)).thenReturn(true);
  WorkspaceMembership m=new WorkspaceMembership();m.setId(27L);
  when(workspaces.selectedMembership(10L)).thenReturn(Optional.of(m));
  assertThrows(PMSCustomException.class,()->access.require(1,Permission.MANAGE_SALE_PIPELINE));
  verify(properties).findByIdAndManagerRoleAndInviteId(1L,10L,"SALES_COORDINATOR",-27L);
 }
}
