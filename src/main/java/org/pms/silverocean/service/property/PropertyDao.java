package org.pms.silverocean.service.property;


import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.PaymentAccountPropertyRepo;
import org.pms.silverocean.database.pms.PropertyAccountRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.entities.Param;
import org.pms.silverocean.database.pms.entities.PaymentAccount;
import org.pms.silverocean.database.pms.entities.PaymentAccountProperty;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.database.pms.entities.PropertyAccount;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.enums.AccountCategory;
import org.pms.silverocean.service.audit.AuditLogService;
import org.pms.silverocean.service.auth.roles.enums.Permission;
import org.pms.silverocean.service.property.wrappers.PropertyDTO;
import org.pms.silverocean.service.wrappers.IdNameDescDTO;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.pms.silverocean.service.property.PropertySpecification.searchProperty;

@Service
public class PropertyDao {
    private final PropertyRepo propertyRepo;
    private final AuditLogService auditLogService;

    private final PropertyAccountRepo propertyAccountRepo;
    private final PaymentAccountPropertyRepo paymentAccountPropertyRepo;

    public PropertyDao(PropertyRepo propertyRepo, AuditLogService auditLogService, PropertyAccountRepo propertyAccountRepo, PaymentAccountPropertyRepo paymentAccountPropertyRepo) {
        this.propertyRepo = propertyRepo;
        this.auditLogService = auditLogService;
        this.propertyAccountRepo = propertyAccountRepo;
        this.paymentAccountPropertyRepo = paymentAccountPropertyRepo;
    }

    public void save(Property property) throws PMSCustomException {
        if (property.getId() == null || findById(property.getId()).isEmpty()) {
            if (property.getCreatedBy() == 0) {
                throw new PMSCustomException(ResponseCode.CREATOR_MUST_BE_SET);
            }
            Optional<Property> optional = propertyRepo.findByNameAndAddressAndCreatedBy(property.getName(), property.getAddress(), property.getCreatedBy());
            if (optional.isPresent()) {
                auditLogService.createAuditLog(property, Permission.CREATE_PROPERTY, "Create new property failed, property already exists", false);
                throw new PMSCustomException(ResponseCode.PROPERTY_ALREADY_EXISTS);
            }
            auditLogService.createAuditLog(property, Permission.CREATE_PROPERTY);
            property.setLastModifiedDate(LocalDateTime.now());
            propertyRepo.save(property);
        } else {
            auditLogService.createAuditLog(property, Permission.CREATE_PROPERTY, "Create new property failed, use of save method to update property", false);
            throw new PMSCustomException(ResponseCode.PROPERTY_ALREADY_EXISTS);
        }
    }

    public void update(Property property) {
        auditLogService.createAuditLog(property, Permission.EDIT_PROPERTY);
        property.setLastModifiedDate(LocalDateTime.now());
        propertyRepo.save(property);
    }

    public void delete(Property property) {
        property.setActive(false);
        property.setHasUnits(false);
        auditLogService.createAuditLog(property, Permission.DELETE_PROPERTY, "User initiated delete property action", true);
        property.setLastModifiedDate(LocalDateTime.now());
        propertyRepo.save(property);
    }

    public void logFailedDeleteAction(Property property, String failureDescription) {
        auditLogService.createAuditLog(property, Permission.DELETE_PROPERTY, failureDescription, false);
    }

    public void updateInactiveProperty(Set<Property> propertySet) {
        propertyRepo.saveAll(propertySet);
        propertySet.forEach(property -> auditLogService.createAuditLog(property, Permission.DELETE_PROPERTY, "Deactivating property with no active units", true));
    }

    public Optional<Property> findById(Long id) {
        return propertyRepo.findById(id);
    }

    public Optional<Property> findByIdAndCreatedBy(Long id, long createdBy) {
        return propertyRepo.findByIdAndCreatedByAndActiveTrue(id, createdBy);
    }

    public Optional<Property> findByIdAndStaffOrOwnerOrTenant(Long id, long userId) {
        return propertyRepo.findByIdAndStaffOrOwnerOrTenant(id, userId);
    }

    public Optional<Property> findByIdAndStaffOrOwner(Long id, long userId) {
        return propertyRepo.findByIdAndStaffOrOwner(id, userId);
    }

    public Optional<Property> findByIdAndTenant(Long id, long userId) {
        return propertyRepo.findByIdAndTenant(id, userId);
    }

    public Optional<Property> findByIdAndManagerRole(Long id, long userId, String roleName) {
        return propertyRepo.findByIdAndManagerRole(id, userId, roleName);
    }

    public Optional<Property> findByIdAndHomeowner(Long id, long userId) { return propertyRepo.findByIdAndHomeowner(id, userId); }
    public Optional<Property> findByIdAndBuyer(Long id, long userId) { return propertyRepo.findByIdAndBuyer(id, userId); }

    public Page<PropertyDTO> findAll(Optional<String> filter, boolean activeOnly, long userId, PMSRole activeRole, Pageable pageable,
                                     BiFunction<Property, Long, String> getUserRoleInProperty, Function<String, String> getSignedImagePath) {

        return propertyRepo.findAll(searchProperty(filter, activeOnly, userId, activeRole), pageable)
                .map(property -> new PropertyDTO(property, getUserRoleInProperty.apply(property, userId),
                        getSignedImagePath.apply(Objects.toString(property.getImagePath(), "") + "/" + Objects.toString(property.getThumbnail(), ""))));
    }

    public Page<IdNameDescDTO> findAllForKeyValue(Optional<String> filter, boolean activeOnly, Long userId, PMSRole activeRole, Pageable pageable) {
        return propertyRepo.findAll(searchProperty(filter, activeOnly, userId, activeRole), pageable)
                .map(property -> new IdNameDescDTO(property.getId(), property.getName()));
    }

    public Page<Property> findPropertyWithoutUnitsToDeactivate(int maxDays, Pageable pageable) {
        return propertyRepo.findAllByHasUnitsFalseAndActiveTrueAndDaysElasped(maxDays, pageable);
    }

    public void saveAllPropertyParams(List<PropertyAccount> paramList) {
        propertyAccountRepo.saveAll(paramList);
    }

    public Set<Param> findPropertyParamByPropertyId(long propertyId) {
        return Set.of();
    }

    public Integer countOccupiedUnitsWithinProperty(long propertyId) {
        return propertyRepo.countOccupiedUnitsWithinProperty(propertyId);
    }

    public Optional<PropertyAccount> findByPropertyIdAndParamIdAndActiveTrue(long propertyId, long paramId) {
        return Optional.empty();
    }

    public Optional<PropertyAccount> findByPropertyIdAndParamTypeAndActiveTrue(long propertyId, String paramType) {
        return Optional.empty();
    }


    public Optional<PaymentAccountProperty> getParamByAccountIdAndTypeAndPropertyId(long accountId, String type, long propertyId) {
        return paymentAccountPropertyRepo.findByAccountIdAndPropertyKeyAndPropertyId(accountId, type, propertyId);
    }

    public Optional<PropertyAccount> findPropertyAccountByIdAndProperty(long accountId, long propertyId) {
        return propertyAccountRepo.findPropertyAccountByIdAndProperty(accountId, propertyId);
    }

    public Optional<PaymentAccount> findIfAccountIsAttachable(long accountId, long userId) {
        return propertyAccountRepo.findByActiveAndCreatedByAndLandlordCategory(accountId, userId, AccountCategory.LANDLORD);
    }

    public void saveAccount(PropertyAccount propertyAccount) {
        propertyAccountRepo.save(propertyAccount);
        auditLogService.createAuditLog(propertyAccount, Permission.EDIT_PROPERTY_PARAM);
    }

    public void removeAccountFromProperty(PropertyAccount propertyAccount) {
        propertyAccount.setActive(false);
        propertyAccountRepo.save(propertyAccount);
        auditLogService.createAuditLog(propertyAccount, Permission.DELETE_PROPERTY_PARAM);
    }

    public Page<IdNameDescDTO> findPropertyLandlords(Pageable pageable, String landlordName) {
        if (StringUtils.isNotBlank(landlordName)) {
            return propertyRepo.findAllLandlordNamesByName(pageable, landlordName);
        }

        return propertyRepo.findAllLandlordNames(pageable);
    }

    public Page<IdNameDescDTO> findPropertyTenants(Pageable pageable, String tenantName) {
        if (StringUtils.isNotBlank(tenantName)) {
            return propertyRepo.findAllTenantNamesByName(pageable, tenantName);
        }

        return propertyRepo.findAllTenantNames(pageable);
    }
}
