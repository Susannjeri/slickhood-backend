package org.pms.silverocean.service.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.Users;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.config.ConfigService;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.param.ParamDao;
import org.pms.silverocean.service.payment.PaymentPlatformFactory;
import org.pms.silverocean.service.property.wrappers.PropertyDTO;
import org.pms.silverocean.service.threadpooling.ThreadPoolBeans;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyCreationTest {
    private PropertyDao propertyDao;
    private UserDao userDao;
    private GarageService garageService;
    private PropertyService service;

    @BeforeEach
    void setUp() {
        propertyDao = mock(PropertyDao.class);
        userDao = mock(UserDao.class);
        garageService = mock(GarageService.class);
        I18NService i18n = mock(I18NService.class);
        var entitlements = mock(org.pms.silverocean.service.subscription.SubscriptionEntitlementService.class);
        when(entitlements.sessionBusinessProduct()).thenReturn(
                org.pms.silverocean.service.subscription.enums.SubscriptionProduct.LANDLORD);
        when(i18n.getLocalizedMessage(any(ResponseCode.class))).thenAnswer(invocation -> invocation.getArgument(0).toString());
        service = new PropertyService(
                propertyDao, mock(UnitDao.class), mock(UnitTypeDao.class), userDao, i18n,
                mock(ParamDao.class), mock(AuditLogService.class), mock(ConfigService.class),
                mock(PMSMeasurementUnitsConverter.class), mock(PropertyRoutines.class), garageService,
                mock(ThreadPoolBeans.class), mock(PaymentPlatformFactory.class), mock(AccountDao.class),
                entitlements, mock(UnitReportDao.class));
        ReflectionTestUtils.setField(service, "imageWidth", 300);
        ReflectionTestUtils.setField(service, "imageHeight", 200);
        ReflectionTestUtils.setField(service, "maxImageBytes", 10L * 1024 * 1024);
        ReflectionTestUtils.setField(service, "maxImagePixels", 40_000_000L);

        Users user = mock(Users.class);
        when(user.isCompletedProfile()).thenReturn(true);
        when(user.getId()).thenReturn(7L);
        when(userDao.getUserObject()).thenReturn(user);
        doAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            property.setId(19L);
            return null;
        }).when(propertyDao).save(any(Property.class));
    }

    @Test
    void creationIsTransactionalAndUsesServerGeneratedImageKey() throws Exception {
        Method method = PropertyService.class.getMethod("createProperty", PropertyDTO.class, org.springframework.web.multipart.MultipartFile.class);
        assertTrue(method.isAnnotationPresent(Transactional.class));

        var response = service.createProperty(request(), validPng());

        assertTrue(response.isSuccess());
        verify(garageService).uploadBytes(eq("7/19/property-cover.png"), any(byte[].class), eq("image/png"));
    }

    @Test
    void storageFailurePropagatesSoTheTransactionCanRollBack() throws Exception {
        doThrow(new PMSCustomException(ResponseCode.GENERAL_FAILURE))
                .when(garageService).uploadBytes(anyString(), any(byte[].class), eq("image/png"));

        PMSCustomException error = assertThrows(PMSCustomException.class,
                () -> service.createProperty(request(), validPng()));

        assertEquals(ResponseCode.GENERAL_FAILURE, error.getResponseCode());
    }

    @Test
    void oversizedUploadIsRejectedBeforePersistence() {
        MockMultipartFile image = new MockMultipartFile("image", "cover.png", "image/png", new byte[10 * 1024 * 1024 + 1]);

        var response = service.createProperty(request(), image);

        assertEquals(ResponseCode.MAX_UPLOAD_SIZE_EXCEEDED.getCode(), response.getCode());
    }

    private PropertyDTO request() {
        return new PropertyDTO("  Sunset Villa  ", PMSPropertyType.STANDALONE_HOUSE,
                PMSPropertyCategory.RESIDENTIAL, PMSPropertyManagementMode.RENTAL,
                " 123 Main Street ", "-1.286389, 36.817223", "kes", null, null, null);
    }

    private MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("image", "../../unsafe name.png", "image/png", output.toByteArray());
    }
}
