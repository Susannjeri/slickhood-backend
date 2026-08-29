package org.pms.silverocean.service.sp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.wrappers.CreateCategoryRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceCategoryDTO;
import org.pms.silverocean.service.wrappers.EnumWrapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCategoryServiceTest {

    @Mock
    private ServiceCategoryDao categoryDao;
    @Mock
    private UserDao userDao;
    @Mock
    private I18NService i18NService;

    private ServiceCategoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ServiceCategoryService(categoryDao, userDao, i18NService);
    }

    // --- createCategory ---

    @Test
    void createCategory_savesCategory_andReturnsDTOWithCorrectName() {
        when(userDao.getUserId()).thenReturn(1L);
        doAnswer(inv -> { ((ServiceCategory) inv.getArgument(0)).setId(1L); return null; })
                .when(categoryDao).save(any(ServiceCategory.class), anyString());

        Set<DocumentType> requiredDocs = Set.of(DocumentType.NATIONAL_ID, DocumentType.GOOD_CONDUCT);
        CreateCategoryRequest request = new CreateCategoryRequest("Home Cleaning", "Cleaning services", requiredDocs, 2);

        ServiceCategoryDTO dto = service.createCategory(request);

        ArgumentCaptor<ServiceCategory> captor = ArgumentCaptor.forClass(ServiceCategory.class);
        verify(categoryDao).save(captor.capture(), anyString());
        ServiceCategory saved = captor.getValue();

        assertEquals("Home Cleaning", saved.getName());
        assertEquals("Cleaning services", saved.getDescription());
        assertEquals(requiredDocs, saved.getRequiredDocumentTypes());
        assertEquals(1L, saved.getCreatedBy());

        assertEquals("Home Cleaning", dto.name());
        assertEquals("Cleaning services", dto.description());
        assertNotNull(dto.requiredDocumentTypes());
        assertEquals(2, dto.requiredDocumentTypes().size());
    }

    @Test
    void createCategory_savesCategory_withNoRequiredDocuments() {
        when(userDao.getUserId()).thenReturn(2L);
        doAnswer(inv -> { ((ServiceCategory) inv.getArgument(0)).setId(2L); return null; })
                .when(categoryDao).save(any(ServiceCategory.class), anyString());

        CreateCategoryRequest request = new CreateCategoryRequest("General Labour", "General work", null, 0);

        ServiceCategoryDTO dto = service.createCategory(request);

        ArgumentCaptor<ServiceCategory> captor = ArgumentCaptor.forClass(ServiceCategory.class);
        verify(categoryDao).save(captor.capture(), anyString());
        assertEquals("General Labour", captor.getValue().getName());
        assertEquals("General Labour", dto.name());
    }

    // --- listRequiredDocumentType ---

    @Test
    void listRequiredDocumentType_returnsSetWithAllDocumentTypes() {
        // i18NService returns the label key as the name (matches DocumentType count)
        when(i18NService.getLocalizedMessage(anyString())).thenAnswer(inv -> inv.getArgument(0).toString());

        Set<EnumWrapper> result = service.listRequiredDocumentType();

        assertNotNull(result);
        // DocumentType has exactly 11 values
        assertEquals(DocumentType.values().length, result.size());
        assertEquals(11, result.size());
    }

    @Test
    void listRequiredDocumentType_eachEnumWrapper_hasNonNullIdAndName() {
        when(i18NService.getLocalizedMessage(anyString())).thenAnswer(inv -> "label:" + inv.getArgument(0).toString());

        Set<EnumWrapper> result = service.listRequiredDocumentType();

        result.forEach(wrapper -> {
            assertNotNull(wrapper.id(), "id should not be null");
            assertNotNull(wrapper.name(), "name should not be null");
        });
    }

    // --- listPricingUnitList ---

    @Test
    void listPricingUnitList_returnsSetWithAllPricingUnits() {
        when(i18NService.getLocalizedMessage(anyString())).thenAnswer(inv -> inv.getArgument(0).toString());

        Set<EnumWrapper> result = service.listPricingUnitList();

        assertNotNull(result);
        // PricingUnit has exactly 11 values
        assertEquals(PricingUnit.values().length, result.size());
        assertEquals(11, result.size());
    }

    @Test
    void listPricingUnitList_containsEnumNames_matchingPricingUnitValues() {
        when(i18NService.getLocalizedMessage(anyString())).thenAnswer(inv -> "translated:" + inv.getArgument(0).toString());

        Set<EnumWrapper> result = service.listPricingUnitList();

        Set<String> expectedIds = new java.util.HashSet<>();
        for (PricingUnit unit : PricingUnit.values()) {
            expectedIds.add(unit.name());
        }
        Set<String> actualIds = new java.util.HashSet<>();
        result.forEach(wrapper -> actualIds.add(wrapper.id()));

        assertEquals(expectedIds, actualIds);
    }
}
