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
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.property.PMSPropertyManagementMode;
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

    @Transactional(readOnly = true)
    public EstateSetupStatus getStatus(long propertyId) {
        Property property = properties.findByIdAndStaffOrOwner(propertyId, users.getUserId())
                .orElseThrow(() -> new PMSCustomException(ResponseCode.PROPERTY_NOT_FOUND));

        long activeUnits = units.countAllByPropertyIdAndActiveTrue(propertyId);
        long activeStaff = managers.countByPropertyIdAndActiveTrue(propertyId);
        long operatingAccounts = accounts.countByPropertyIdAndActiveTrue(propertyId);
        long activeHomeowners = ownerships.countByPropertyIdAndActiveTrue(propertyId);
        long currentBudgets = budgets.countByPropertyIdAndBudgetYearAndActiveTrue(propertyId, Year.now().getValue());
        boolean unitsConfigured = activeUnits > 0;
        boolean billingConfigured = operatingAccounts > 0;
        boolean serviceCharge = property.getManagementMode() == PMSPropertyManagementMode.SERVICE_CHARGE;
        boolean homeownerOperationsConfigured = !serviceCharge || activeHomeowners > 0 && currentBudgets > 0;
        boolean ready = unitsConfigured && billingConfigured && homeownerOperationsConfigured;

        return new EstateSetupStatus(propertyId, property.getName(), property.getManagementMode(), activeUnits,
                activeStaff, operatingAccounts, activeHomeowners, currentBudgets, unitsConfigured,
                billingConfigured, homeownerOperationsConfigured, ready,
                nextAction(serviceCharge, activeUnits, operatingAccounts, activeHomeowners, currentBudgets, activeStaff));
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
