package org.pms.silverocean.service.estate;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.database.pms.EstateBudgetRepo;
import org.pms.silverocean.database.pms.PropertyAccountRepo;
import org.pms.silverocean.database.pms.PropertyManagerRepo;
import org.pms.silverocean.database.pms.PropertyOwnershipRepo;
import org.pms.silverocean.database.pms.PropertyRepo;
import org.pms.silverocean.database.pms.UnitRepo;
import org.pms.silverocean.database.pms.entities.Property;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.auth.roles.enums.PMSRole;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
import org.pms.silverocean.service.teamaccess.WorkspaceSelectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class EstateSetupService {
    private final PropertyRepo properties;
    private final UnitRepo units;
    private final PropertyManagerRepo managers;
    private final PropertyAccountRepo accounts;
    private final PropertyOwnershipRepo ownerships;
    private final EstateBudgetRepo budgets;
    private final UserDao users;
    private final WorkspaceSelectionService workspaces;

    @Transactional(readOnly = true)
    public EstateSetupStatus getStatus(long propertyId) {
        Property property = findAccessibleProperty(propertyId)
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));

        PMSPropertyManagementMode contextMode = contextMode(property);
        String unitMode = switch (contextMode) {
            case RENTAL -> "RENT";
            case SERVICE_CHARGE -> "SERVICE_CHARGE";
            case SALE -> "SALE";
        };
        long activeUnits = units.countByPropertyIdAndLeaseModeAndActiveTrue(propertyId, unitMode);
        long activeStaff = managers.countByPropertyIdAndActiveTrue(propertyId);
        org.pms.silverocean.service.account.enums.AccountCategory category = switch (contextMode) {
            case SERVICE_CHARGE -> org.pms.silverocean.service.account.enums.AccountCategory.ESTATE_MANAGEMENT;
            case SALE -> org.pms.silverocean.service.account.enums.AccountCategory.PROPERTY_SALES;
            default -> org.pms.silverocean.service.account.enums.AccountCategory.LANDLORD;
        };
        long operatingAccounts = accounts.countVerifiedOperatingAccounts(propertyId, category);
        long activeHomeowners = ownerships.countByPropertyIdAndActiveTrue(propertyId);
        long currentBudgets = budgets.countByPropertyIdAndBudgetYearAndStatusAndActiveTrue(propertyId,
                Year.now(org.pms.silverocean.common.PMSUtils.getZoneId()).getValue(), "APPROVED");
        boolean unitsConfigured = activeUnits > 0;
        boolean billingConfigured = operatingAccounts > 0;
        boolean serviceCharge = contextMode == PMSPropertyManagementMode.SERVICE_CHARGE;
        boolean homeownerOperationsConfigured = !serviceCharge || activeHomeowners > 0 && currentBudgets > 0;
        boolean ready = unitsConfigured && billingConfigured && homeownerOperationsConfigured;

        return new EstateSetupStatus(propertyId, property.getName(), contextMode, activeUnits,
                activeStaff, operatingAccounts, activeHomeowners, currentBudgets, unitsConfigured,
                billingConfigured, homeownerOperationsConfigured, ready,
                nextAction(serviceCharge, activeUnits, operatingAccounts, activeHomeowners, currentBudgets, activeStaff));
    }

    private java.util.Optional<Property> findAccessibleProperty(long propertyId) {
        PMSRole role = users.getActiveRole();
        long userId = users.getUserId();
        if (role == PMSRole.SUPER_ADMIN) return properties.findById(propertyId).filter(Property::isActive);
        if (role == PMSRole.LANDLORD || role == PMSRole.ESTATE_MANAGER || role == PMSRole.SALES_AGENT) {
            return properties.findByIdAndCreatedByAndActiveTrue(propertyId, userId);
        }
        if (role == null || !role.isCustomerEmployeeRole()) return java.util.Optional.empty();
        return workspaces.selectedMembership(userId)
                .flatMap(membership -> properties.findByIdAndManagerRoleAndInviteId(
                        propertyId, userId, role.name(), -membership.getId()));
    }

    private PMSPropertyManagementMode contextMode(Property property) {
        PMSRole role = users.getActiveRole();
        if (role == null) return property.getManagementMode();
        return switch (role) {
            case LANDLORD, TENANT -> PMSPropertyManagementMode.RENTAL;
            case ESTATE_MANAGER, HOMEOWNER -> PMSPropertyManagementMode.SERVICE_CHARGE;
            case SALES_AGENT, BUYER -> PMSPropertyManagementMode.SALE;
            default -> property.getManagementMode();
        };
    }

    private EstateSetupStatus.NextAction nextAction(boolean serviceCharge, long activeUnits, long operatingAccounts,
                                                     long activeHomeowners, long currentBudgets, long activeStaff) {
        if (activeUnits == 0) return EstateSetupStatus.NextAction.ADD_UNITS;
        if (operatingAccounts == 0) return EstateSetupStatus.NextAction.LINK_OPERATING_ACCOUNT;
        if (serviceCharge && activeHomeowners == 0) return EstateSetupStatus.NextAction.ASSIGN_HOMEOWNERS;
        if (serviceCharge && currentBudgets == 0) return EstateSetupStatus.NextAction.CREATE_ESTATE_BUDGET;
        if (activeStaff == 0) return EstateSetupStatus.NextAction.INVITE_ESTATE_TEAM;
        return EstateSetupStatus.NextAction.READY;
    }
}
