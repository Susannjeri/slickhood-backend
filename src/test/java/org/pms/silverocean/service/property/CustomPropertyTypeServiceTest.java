package org.pms.silverocean.service.property;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.database.pms.CustomPropertyTypeRepo;
import org.pms.silverocean.database.pms.entities.*;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.PMSCustomException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CustomPropertyTypeServiceTest {
 private final CustomPropertyTypeRepo repo=mock(CustomPropertyTypeRepo.class);private final UserDao users=mock(UserDao.class);private final AuditLogService audit=mock(AuditLogService.class);private final CustomPropertyTypeService service=new CustomPropertyTypeService(repo,users,audit);
 private CustomPropertyType type(){var t=new CustomPropertyType();t.setCode("CUSTOM_CO_LIVING");t.setName("Co-living");t.setCategory(PMSPropertyCategory.RESIDENTIAL);t.setActive(true);t.setUnitTypes(new HashSet<>(Set.of(PMSUnitTypes.STUDIO)));return t;}
 @Test void creationCannotGrantAdditionalRoles(){assertThrows(PMSCustomException.class,()->service.create(new CustomPropertyTypeService.Create("CUSTOM_CO_LIVING","Co-living","",PMSPropertyCategory.RESIDENTIAL,Set.of(PMSUnitTypes.STUDIO),"Create")));verifyNoInteractions(repo,audit);}
 @Test void createsAuditedTypeWithExistingUnitCodes(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var r=service.create(new CustomPropertyTypeService.Create("CUSTOM_CO_LIVING"," Co-living ","Shared living",PMSPropertyCategory.RESIDENTIAL,Set.of(PMSUnitTypes.STUDIO),"Approved category"));assertEquals("Co-living",r.getName());assertTrue(r.isActive());assertEquals(Set.of(PMSUnitTypes.STUDIO),r.getUnitTypes());verify(audit).createAuditLog(r,"PROPERTY_TYPE_CREATE","Approved category",true);}
 @Test void staleEditsCannotOverwriteMappings(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var t=type();t.setVersion(2);when(repo.findForUpdate(t.getCode())).thenReturn(Optional.of(t));assertThrows(PMSCustomException.class,()->service.edit(t.getCode(),new CustomPropertyTypeService.Edit("Other","",true,Set.of(PMSUnitTypes.ONE_BEDROOM),1,"change")));assertEquals(Set.of(PMSUnitTypes.STUDIO),t.getUnitTypes());verifyNoInteractions(audit);}
 @Test void retirementPreservesCategoryAndMappings(){when(users.hasRole(PMSRole.SUPER_ADMIN)).thenReturn(true);var t=type();when(repo.findForUpdate(t.getCode())).thenReturn(Optional.of(t));service.edit(t.getCode(),new CustomPropertyTypeService.Edit(t.getName(),"",false,t.getUnitTypes(),0,"Retire"));assertFalse(t.isActive());assertEquals(PMSPropertyCategory.RESIDENTIAL,t.getCategory());assertEquals(Set.of(PMSUnitTypes.STUDIO),t.getUnitTypes());}
 @Test void dtoReadsNeverAttemptEnumConversionOnCustomTypes(){var p=Property.builder().name("Shared home").type("CUSTOM_CO_LIVING").typeCategory(PMSPropertyCategory.RESIDENTIAL).build();var dto=new org.pms.silverocean.service.property.wrappers.PropertyDTO(p,"Owner",null);assertEquals("CUSTOM_CO_LIVING",dto.type());assertEquals(PMSPropertyCategory.RESIDENTIAL,dto.category());assertEquals("CUSTOM_CO_LIVING",new org.pms.silverocean.service.property.wrappers.PropertyViewDTO(p,null).type());}
 @Test void customTypesAreValidatedWithoutChangingBuiltInMappings(){var dao=new UnitTypeDao(mock(org.pms.silverocean.database.pms.UnitTypeMappingRepo.class),audit);dao.setCustomPropertyTypes(service);var t=type();when(repo.findByCode(t.getCode())).thenReturn(Optional.of(t));assertEquals(PMSPropertyCategory.RESIDENTIAL,dao.requireType(t.getCode()));assertTrue(dao.isAllowedCode(t.getCode(),PMSUnitTypes.STUDIO));assertFalse(dao.isAllowedCode(t.getCode(),PMSUnitTypes.ONE_BEDROOM));t.setActive(false);assertThrows(PMSCustomException.class,()->dao.requireType(t.getCode()));assertFalse(dao.isAllowedCode(t.getCode(),PMSUnitTypes.STUDIO));}
}
