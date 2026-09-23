package org.pms.silverocean.service.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.param.ParamDao;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;

class PropertyServiceAuthorizationTest {

    private PropertyDao propertyDao;
    private UnitDao unitDao;
    private UserDao userDao;
    private UnitTypeDao unitTypeDao;
    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyDao = mock(PropertyDao.class);
        unitDao = mock(UnitDao.class);
        userDao = mock(UserDao.class);
        unitTypeDao=mock(UnitTypeDao.class);
        propertyService = new PropertyService(
                propertyDao,
                unitDao,
                unitTypeDao,
                userDao,
                mock(I18NService.class),
                mock(ParamDao.class),
                mock(AuditLogService.class),
                mock(ConfigService.class),
                mock(PMSMeasurementUnitsConverter.class),
                mock(PropertyRoutines.class),
                mock(GarageService.class),
                mock(ThreadPoolBeans.class),
                mock(PaymentPlatformFactory.class),
                mock(AccountDao.class),
                mock(org.pms.silverocean.service.subscription.SubscriptionEntitlementService.class),
                mock(UnitReportDao.class),
                mock(org.pms.silverocean.service.teamaccess.WorkspaceSelectionService.class),
                mock(org.pms.silverocean.database.pms.UnitTenantRepo.class),
                mock(org.pms.silverocean.database.pms.SaleTransactionRepo.class),
                mock(org.pms.silverocean.database.pms.PropertyOwnershipRepo.class),
                mock(org.pms.silverocean.database.pms.EstateServiceChargeRepo.class),
                mock(org.pms.silverocean.database.pms.PropertyListingRepo.class),
                mock(org.pms.silverocean.database.pms.PMSInvoiceRepo.class));
        when(userDao.getUserId()).thenReturn(7L);
    }

    @Test
    void editUnitRejectsMovingUnitToPropertyNotOwnedByCurrentUser() {
        UnitDTO request = new UnitDTO(99L, "A-1", null, 10.0,
                new MeasurementUnitsDTO(1, "sqm"), Set.of(), null,
                1000.0, "KES", 1L);
        when(propertyDao.findByIdAndStaffOrOwner(99L, 7L)).thenReturn(Optional.empty());

        ResponseDTO response = propertyService.editUnit(11L, request, null);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY.getCode(), response.getCode());
        verify(unitDao, never()).findByIdAndCreatedBy(11L, 7L);
    }
    @Test void unrelatedEditCanRetainDisabledLegacyType(){
        var property=new Property();property.setId(99L);property.setType(PMSPropertyType.APARTMENT_BLOCK.name());property.setHasUnits(true);
        var unit=new org.pms.silverocean.database.pms.entities.Unit();unit.setId(11L);unit.setPropertyId(99L);unit.setUnitType(PMSUnitTypes.STUDIO.name());unit.setLeaseMode("RENT");
        when(userDao.getActiveRole()).thenReturn(org.pms.silverocean.service.auth.roles.enums.PMSRole.LANDLORD);
        when(propertyDao.findByIdAndCreatedBy(99L,7L)).thenReturn(Optional.of(property));when(unitDao.findByIdAndCreatedBy(11L,7L)).thenReturn(Optional.of(unit));
        var request=new UnitDTO(99L,"Edited reference",PMSUnitTypes.STUDIO,10.0,new MeasurementUnitsDTO(1,"sqm"),Set.of(),PMSLeaseMode.RENT,1000.0,"KES",null);
        assertTrue(propertyService.editUnit(11L,request,null).isSuccess());verify(unitTypeDao,never()).isAllowed(any(),any());verify(unitDao).update(unit);
    }
    @Test void editCannotChangeToDisabledType(){
        var property=new Property();property.setId(99L);property.setType(PMSPropertyType.APARTMENT_BLOCK.name());
        var unit=new org.pms.silverocean.database.pms.entities.Unit();unit.setId(11L);unit.setPropertyId(99L);unit.setUnitType(PMSUnitTypes.STUDIO.name());unit.setLeaseMode("RENT");
        when(userDao.getActiveRole()).thenReturn(org.pms.silverocean.service.auth.roles.enums.PMSRole.LANDLORD);
        when(propertyDao.findByIdAndCreatedBy(99L,7L)).thenReturn(Optional.of(property));when(unitDao.findByIdAndCreatedBy(11L,7L)).thenReturn(Optional.of(unit));
        var request=new UnitDTO(99L,"A1",PMSUnitTypes.ONE_BEDROOM,10.0,new MeasurementUnitsDTO(1,"sqm"),Set.of(),PMSLeaseMode.RENT,1000.0,"KES",null);
        assertFalse(propertyService.editUnit(11L,request,null).isSuccess());verify(unitDao,never()).update(any());
    }

    @Test
    void authenticatedUnitChargesRejectInaccessibleUnit() {
        when(unitDao.findByIdAndStaffOrOwnerOrTenant(11L, 7L)).thenReturn(Optional.empty());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> propertyService.getUnitCharges(null, 11L));

        assertEquals(ResponseCode.UNIT_NOT_FOUND, error.getResponseCode());
    }

    @Test
    void landlordAndManagerDetailsRejectInaccessibleUnit() {
        when(unitDao.findByIdAndStaffOrOwnerOrTenant(11L, 7L)).thenReturn(Optional.empty());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> propertyService.listUnitLandlordAndManagers(11L));

        assertEquals(ResponseCode.UNIT_NOT_FOUND, error.getResponseCode());
    }

    @Test
    void mixedUsePropertyAcceptsAnyOwnedPropertyPaymentAccountCategory() {
        Property property = new Property();
        property.setManagementMode(PMSPropertyManagementMode.SERVICE_CHARGE);
        PaymentAccount account = new PaymentAccount();
        account.setVerified(true);
        account.setCategory(AccountCategory.ESTATE_MANAGEMENT);
        when(propertyDao.findByIdAndCreatedBy(9L, 7L)).thenReturn(Optional.of(property));
        when(propertyDao.findActiveOwnedAccount(12L, 7L))
                .thenReturn(Optional.of(account));
        when(propertyDao.findPropertyAccountByIdAndProperty(12L, 9L)).thenReturn(Optional.empty());

        propertyService.attachAccountToProperty(12L, 9L);

        verify(propertyDao).saveAccount(any());
        verify(propertyDao).findActiveOwnedAccount(12L, 7L);
    }

    @Test
    void ownerCanOpenCreatedPropertyAfterSwitchingToHomeownerRole() {
        Property property = new Property();
        property.setId(99L);
        property.setActive(true);
        property.setCreatedBy(7L);
        Users user = mock(Users.class);
        when(user.isCompletedProfile()).thenReturn(true);
        when(user.getId()).thenReturn(7L);
        when(userDao.getUserObject()).thenReturn(user);
        when(userDao.getActiveRole()).thenReturn(PMSRole.HOMEOWNER);
        when(propertyDao.findByIdAndCreatedBy(99L, 7L)).thenReturn(Optional.of(property));

        ResponseDTO response = propertyService.listProperty(
                org.springframework.data.domain.PageRequest.of(0, 10), Optional.empty(),
                Optional.of(99L), Optional.empty(), Optional.empty(), (candidate, userId) -> "LANDLORD");

        assertTrue(response.isSuccess());
        assertEquals(ResponseCode.PROPERTY_DETAILS.getCode(), response.getCode());
        verify(propertyDao, never()).findByIdAndHomeowner(99L, 7L);
    }
}
