package org.pms.silverocean.service.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ResponseDTO;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.param.ParamDao;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyServiceAuthorizationTest {

    private PropertyDao propertyDao;
    private UnitDao unitDao;
    private UserDao userDao;
    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyDao = mock(PropertyDao.class);
        unitDao = mock(UnitDao.class);
        userDao = mock(UserDao.class);
        propertyService = new PropertyService(
                propertyDao,
                unitDao,
                mock(UnitTypeDao.class),
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
                mock(AccountDao.class));
        when(userDao.getUserId()).thenReturn(7L);
    }

    @Test
    void editUnitRejectsMovingUnitToPropertyNotOwnedByCurrentUser() {
        UnitDTO request = new UnitDTO(99L, "A-1", null, 10.0,
                new MeasurementUnitsDTO(1, "sqm"), Set.of(), null,
                1000.0, "KES", 1L);
        when(propertyDao.findByIdAndCreatedBy(99L, 7L)).thenReturn(Optional.empty());

        ResponseDTO response = propertyService.editUnit(11L, request, null);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCode.UNIT_CREATION_FAILED_MISSING_PROPERTY.getCode(), response.getCode());
        verify(unitDao, never()).findByIdAndCreatedBy(11L, 7L);
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
}
