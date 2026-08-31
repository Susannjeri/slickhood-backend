package org.pms.silverocean.service.estate;

import org.pms.silverocean.service.property.PMSPropertyManagementMode;

public record EstateSetupStatus(
        long propertyId,
        String propertyName,
        PMSPropertyManagementMode managementMode,
        long activeUnits,
        long activeStaff,
        long operatingAccounts,
        long activeHomeowners,
        long currentBudgets,
        boolean unitsConfigured,
        boolean billingConfigured,
        boolean homeownerOperationsConfigured,
        boolean readyForHomeownerOperations,
        NextAction nextAction
) {
    public enum NextAction {
        ADD_UNITS,
        LINK_OPERATING_ACCOUNT,
        ASSIGN_HOMEOWNERS,
        CREATE_ESTATE_BUDGET,
        INVITE_ESTATE_TEAM,
        READY
    }
}
