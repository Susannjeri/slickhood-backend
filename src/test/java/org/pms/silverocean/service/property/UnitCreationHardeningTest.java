package org.pms.silverocean.service.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.controller.wrappers.ChargeDTO;
import org.pms.silverocean.controller.wrappers.UnitChargesDTO;
import org.pms.silverocean.database.pms.entities.ChargeType;
import org.pms.silverocean.database.pms.entities.BulkUnitJob;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Unit;
import org.pms.silverocean.database.pms.entities.Utility;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.lease.wrappers.PMSLeaseMode;
import org.pms.silverocean.service.property.wrappers.DuplicateUnitJobDTO;
import org.pms.silverocean.service.param.ParamDao;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.charges.PMSPeriod;
import org.pms.silverocean.service.property.wrappers.UnitDTO;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

class UnitCreationHardeningTest {
    private PropertyDao properties;
    private UnitDao units;
    private UserDao users;
    private GarageService storage;
    private PropertyService service;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyDao.class);
        units = mock(UnitDao.class);
        users = mock(UserDao.class);
        storage = mock(GarageService.class);
        I18NService i18n = mock(I18NService.class);
        when(i18n.getLocalizedMessage(any(ResponseCode.class))).thenAnswer(call -> call.getArgument(0).toString());
        service = new PropertyService(properties, units, mock(UnitTypeDao.class), users, i18n,
                mock(ParamDao.class), mock(AuditLogService.class), mock(ConfigService.class),
                mock(PMSMeasurementUnitsConverter.class), mock(PropertyRoutines.class), storage,
                mock(ThreadPoolBeans.class), mock(PaymentPlatformFactory.class), mock(AccountDao.class));
        ReflectionTestUtils.setField(service, "imageWidth", 300);
        ReflectionTestUtils.setField(service, "imageHeight", 200);
        ReflectionTestUtils.setField(service, "maxImageBytes", 10L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "maxImagePixels", 40_000_000L);
        when(users.getUserId()).thenReturn(77L);
        Utility utility = new Utility();
        utility.setId(1L);
        utility.setActive(true);
        when(units.getUtilities(1L)).thenReturn(Optional.of(utility));
    }

    @Test
    void createUnitIsTransactionalAndAllowsAuthorizedEstateStaff() throws Exception {
        Property property = property(PMSPropertyManagementMode.SERVICE_CHARGE);
        when(properties.findByIdAndStaffOrOwner(9L, 77L)).thenReturn(Optional.of(property));
        doAnswer(call -> { ((Unit) call.getArgument(0)).setId(31L); return null; }).when(units).save(any(Unit.class));

        var response = service.createUnit(request(PMSLeaseMode.SERVICE_CHARGE), validPng());

        Method method = PropertyService.class.getMethod("createUnit", UnitDTO.class, org.springframework.web.multipart.MultipartFile.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        verify(properties).findByIdAndStaffOrOwner(9L, 77L);
        verify(units).save(any(Unit.class));
        verify(storage).uploadBytes(eq("77/9/31/unit-cover.png"), any(), eq("image/png"));
    }

    @Test
    void unitWorkflowMustMatchThePropertyWorkflow() throws Exception {
        when(properties.findByIdAndStaffOrOwner(9L, 77L)).thenReturn(Optional.of(property(PMSPropertyManagementMode.SERVICE_CHARGE)));

        var response = service.createUnit(request(PMSLeaseMode.RENT), validPng());

        assertThat(response.getCode()).isEqualTo(ResponseCode.INVALID_FIELD_DATA.getCode());
        verify(units, never()).save(any(Unit.class));
    }

    @Test
    void storageFailurePropagatesInsteadOfBeingReportedAsADuplicate() throws Exception {
        when(properties.findByIdAndStaffOrOwner(9L, 77L)).thenReturn(Optional.of(property(PMSPropertyManagementMode.SERVICE_CHARGE)));
        doAnswer(call -> { ((Unit) call.getArgument(0)).setId(31L); return null; }).when(units).save(any(Unit.class));
        doThrow(new PMSCustomException(ResponseCode.GENERAL_FAILURE)).when(storage).uploadBytes(anyString(), any(), anyString());

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.createUnit(request(PMSLeaseMode.SERVICE_CHARGE), validPng()));

        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.GENERAL_FAILURE);
    }

    @Test
    void duplicateChargeTypesAreRejectedAtTheServiceBoundary() {
        Unit unit = new Unit();
        unit.setId(31L);
        when(units.findByIdAndStaffOrOwner(31L, 77L)).thenReturn(Optional.of(unit));
        ChargeType chargeType = new ChargeType();
        chargeType.setId(5L);
        chargeType.setActive(true);
        when(units.getChargeType(5L)).thenReturn(Optional.of(chargeType));
        UnitChargesDTO request = new UnitChargesDTO(31L, Set.of(
                new ChargeDTO(5L, PMSPeriod.MONTHLY, 100D),
                new ChargeDTO(5L, PMSPeriod.ANNUAL, 1000D)));

        PMSCustomException error = assertThrows(PMSCustomException.class, () -> service.updateUnitCharges(request));

        assertThat(error.getResponseCode()).isEqualTo(ResponseCode.INVALID_FIELD_DATA);
        verify(units, never()).updateUnitCharges(anyLong(), any());
    }

    @Test
    void bulkCopyCountMustBePositive() {
        var response = service.createDuplicateJob(31L, 0);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ResponseCode.NUMBER_EXCEEDS_ALLOWED_LIMIT.getCode());
        verify(units, never()).findByIdAndStaffOrOwner(31L, 77L);
    }

    @Test
    void bulkCopyJobFinishesAsFailedWhenItsSourceIsNoLongerAccessible() {
        BulkUnitJob job = new BulkUnitJob();
        job.setId(55L);
        job.setUnitId(31L);
        job.setCreatedBy(77L);
        job.setEmail("manager@example.com");
        job.setActive(true);
        when(units.findActiveJobById(55L)).thenReturn(Optional.of(job));
        when(units.findByIdAndStaffOrOwner(31L, 77L)).thenReturn(Optional.empty());

        DuplicateUnitJobDTO result = ReflectionTestUtils.invokeMethod(service, "runDuplicateJob", 55L);

        assertThat(result).isNotNull();
        assertThat(result.success()).isFalse();
        assertThat(job.isCompleted()).isTrue();
        verify(units).updateBulkUnitJob(job);
    }

    private Property property(PMSPropertyManagementMode mode) {
        Property property = new Property();
        property.setId(9L);
        property.setName("Green Court");
        property.setCurrency("KES");
        property.setManagementMode(mode);
        property.setActive(true);
        return property;
    }

    private UnitDTO request(PMSLeaseMode mode) {
        return new UnitDTO(9L, " A-01 ", PMSUnitTypes.APARTMENT_UNIT, 80D,
                new MeasurementUnitsDTO(1, "Square metres"), Set.of(1L), mode, 5000D, "KES", null);
    }

    private MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("image", "unit.png", "image/png", output.toByteArray());
    }
}
