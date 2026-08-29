package org.pms.silverocean.service.sp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.entities.ServiceCategory;
import org.pms.silverocean.service.I18NService;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.sp.dao.ServiceCategoryDao;
import org.pms.silverocean.service.sp.enums.DocumentType;
import org.pms.silverocean.service.sp.enums.PricingUnit;
import org.pms.silverocean.service.sp.wrappers.CreateCategoryRequest;
import org.pms.silverocean.service.sp.wrappers.ServiceCategoryDTO;
import org.pms.silverocean.service.wrappers.EnumWrapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServiceCategoryService {
    private final ServiceCategoryDao categoryDao;
    private final UserDao userDao;
    private final I18NService i18NService;

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceCategoryDTO createCategory(CreateCategoryRequest request) {
        long userId = userDao.getUserId();
        ServiceCategory category = new ServiceCategory();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setRequiredDocumentTypes(request.requiredDocumentTypes());
        category.setRequiredNumberOfReferees(request.requiredNumberOfReferees());
        category.setActive(true);
        category.setCreatedBy(userId);
        categoryDao.save(category, Permission.MANAGE_SP_CATEGORIES);
        return new ServiceCategoryDTO(category);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public ServiceCategoryDTO updateCategory(long categoryId, CreateCategoryRequest request) {
        ServiceCategory category = categoryDao.findById(categoryId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        category.setName(request.name());
        category.setDescription(request.description());
        category.setRequiredDocumentTypes(request.requiredDocumentTypes());
        category.setRequiredNumberOfReferees(request.requiredNumberOfReferees());
        categoryDao.save(category, Permission.MANAGE_SP_CATEGORIES);
        return new ServiceCategoryDTO(category);
    }

    @Transactional(transactionManager = "pmsDBTransactionManager")
    public void deactivateCategory(long categoryId) {
        ServiceCategory category = categoryDao.findById(categoryId)
                .filter(ServiceCategory::isActive)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.SP_CATEGORY_NOT_FOUND));
        category.setActive(false);
        categoryDao.save(category, Permission.MANAGE_SP_CATEGORIES);
    }

    public Page<ServiceCategoryDTO> listCategories(Pageable pageable) {
        return categoryDao.findAllActive(pageable).map(ServiceCategoryDTO::new);
    }

    public Set<EnumWrapper> listRequiredDocumentType() {
        return EnumSet.allOf(DocumentType.class).stream()
                .map(documentType -> new EnumWrapper(documentType.name(), i18NService.getLocalizedMessage(documentType.getLabel()), null))
                .collect(Collectors.toSet());
    }

    public Set<EnumWrapper> listPricingUnitList() {
        return EnumSet.allOf(PricingUnit.class).stream()
                .map(pricingUnit -> new EnumWrapper(pricingUnit.name(), i18NService.getLocalizedMessage(pricingUnit.getLabel()), null))
                .collect(Collectors.toSet());
    }
}
