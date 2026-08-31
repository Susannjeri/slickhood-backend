package org.pms.silverocean.service.auth.roles.enums;

import lombok.Getter;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
public enum PMSRole {
    LANDLORD("Landlord", "Property Owners configure their real estate holdings by creating property records and defining unit layouts.", true),
    SERVICE_PROVIDER("ServiceProvider", "Registers to offer one or more service categories (e.g., cleaning, pest control, electrical)", true),
    ASSET_PORTFOLIO_MANAGER("AssetPortfolioManager", "SlickHood Wealth owner: tracks assets, net worth, performance, obligations and goals", true),
    AFFILIATE("Affiliate", "Recruits users (Landlords, Service Providers)through unique links, earns commissions", true),
    TENANT("Tenant", "Receives invitation, submits documentation, accepts lease terms", false),
    PROPERTY_MANAGER("PropertyManager", "Supports onboarding, handles approvals and lease activations", false),
    WORKSPACE_ADMIN("WorkspaceAdmin", "Delegated customer workspace administrator", false),
    PROPERTY_ACCOUNTANT("PropertyAccountant", "Customer-side property finance and accounting team member", false),
    LEASING_OFFICER("LeasingOfficer", "Customer-side leasing operations team member", false),
    ESTATE_OPERATIONS_MANAGER("EstateOperationsManager", "Customer-side estate operations team member", false),
    SECURITY_SUPERVISOR("SecuritySupervisor", "Customer-side estate security supervisor", false),
    SALES_COORDINATOR("SalesCoordinator", "Customer-side property sales coordinator", false),
    LISTING_AGENT("ListingAgent", "Customer-side property listing team member", false),
    WORKSPACE_VIEWER("WorkspaceViewer", "Read-only customer workspace member", false),
    ESTATE_MANAGER("EstateManager", "Manages estates, homeowners, service charges, common areas and community operations", true),
    HOMEOWNER("Homeowner", "Owns an estate property or unit and manages service charges, visitors and service requests", false),
    SALES_AGENT("SalesAgent", "Manages property sale listings, buyers, offers, due diligence and completion", true),
    BUYER("Buyer", "Reviews a property purchase, offer, due diligence, payments and handover", false),
    INSURANCE_ADVISER("InsuranceAdviser", "Authorised Silverwood adviser who reviews insurance applications, quotations, claims and renewals", false),
    INSURANCE_MANAGER("InsuranceManager", "Authorised Silverwood manager who maintains insurers, payment destinations and policy operations", false),
    SUPPORT("Support", "SlickHood support staff who assist users and manage help-desk cases", false),
    SALES_MARKETING("SalesMarketing", "SlickHood sales and marketing staff", false),
    FINANCE("Finance", "", false),
    GUARD("Guard", "Manages live check-ins and check-outs at property gates", false),
    SUPER_ADMIN("Superadmin", "Maintains functionality of the application", false);

    private final String name;
    private final String description;
    private final boolean selfAssignable;

    private static final Map<String, PMSRole> nameToRole = new HashMap<>();
    private static final Set<PMSRole> CUSTOMER_EMPLOYEE_ROLES = Set.of(
            WORKSPACE_ADMIN,
            PROPERTY_MANAGER,
            PROPERTY_ACCOUNTANT,
            LEASING_OFFICER,
            ESTATE_OPERATIONS_MANAGER,
            SECURITY_SUPERVISOR,
            GUARD,
            SALES_COORDINATOR,
            LISTING_AGENT,
            WORKSPACE_VIEWER
    );

    public boolean isPlatformOwnerOnly() {
        return this == SUPER_ADMIN;
    }

    public boolean isCustomerEmployeeRole() {
        return CUSTOMER_EMPLOYEE_ROLES.contains(this);
    }

    public static PMSRole roleFromSavedName(String name) {
        if (!nameToRole.containsKey(name)) {
            throw new PMSCustomException(ResponseCode.INVALID_ROLE);
        }
        return nameToRole.get(name);
    }

    static {
        for (PMSRole role : PMSRole.values()) {
            nameToRole.put(role.getName(), role);
        }
    }

    PMSRole(String name, String description, boolean selfAssignable) {
        this.name = name;
        this.description = description;
        this.selfAssignable = selfAssignable;
    }
}
