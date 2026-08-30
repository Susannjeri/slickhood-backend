package org.pms.silverocean.service.auth.roles.enums;

import lombok.Getter;

import java.util.Set;

import static org.pms.silverocean.service.auth.roles.enums.Permission.ADVERTISE_UNIT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CANCEL_VISITOR;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_ACCOUNT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DECRYPT_ACCOUNT_PROPERTY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_ACCOUNT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_VISITOR;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_INVITE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_NEW_LEASE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_PROPERTY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_SUBSCRIPTION_PLAN;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_UNIT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_PROPERTY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_PROPERTY_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_PROPERTY_STAFF;
import static org.pms.silverocean.service.auth.roles.enums.Permission.DELETE_UNIT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_ACCOUNT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_CONFIG;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_LEASE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_PROPERTY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_PROPERTY_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_SUBSCRIPTION_PLAN;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_UNIT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_UNIT_CHARGES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.LIST_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.LIST_USERS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INTERNAL_STAFF;
import static org.pms.silverocean.service.auth.roles.enums.Permission.RECORD_MANUAL_PAYMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REGISTER_VISITOR;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ROTATE_KEY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SEND_LEASE_MESSAGE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SHARE_INVITE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SIGN_LEASE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.UPDATE_INVITE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.UPDATE_VISITOR_STATUS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VERIFY_ACCOUNT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VERIFY_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ACCOUNT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ACTIVE_LEASE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ALL_ACCOUNTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ALL_PARAMS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_AUDIT_LOGS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_CONFIG;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_INVITE_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_INVOICE_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_INVOICE_PDF;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_LANDLORD_AND_MANAGERS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_LEASE_INVITE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_LEASE_MESSAGE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_LEASE_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_NOTIFICATIONS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_MY_NOTIFICATIONS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PAYMENT_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY_LANDLORDS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY_PARAM;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY_STAFF;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_PROPERTY_TENANTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SUBSCRIPTION_PLAN;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_TENANTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_UNIT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_UNIT_CHARGES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_UNIT_LEASE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_UNIT_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_VISITOR_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ADD_SP_REFEREE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_SP_REFEREE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REMOVE_SP_REFEREE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ADD_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.APPROVE_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ASSIGN_SP_TIER;
import static org.pms.silverocean.service.auth.roles.enums.Permission.BLACKLIST_SP;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CANCEL_SP_BOOKING;
import static org.pms.silverocean.service.auth.roles.enums.Permission.COMPLETE_SP_BOOKING;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CONFIRM_SP_BOOKING;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_SP_BOOKING;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.FILE_SP_COMPLAINT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_SP_CATEGORIES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.RATE_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REMOVE_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.RESOLVE_SP_COMPLAINT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REVIEW_SP_COMPLAINT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SETUP_SP_PROFILE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SUSPEND_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.UPLOAD_SP_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VERIFY_SP_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VERIFY_SP_REFEREE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_BOOKING;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_CATEGORY_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_LIST;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_PROFILE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_RATINGS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SP_SERVICE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.EDIT_SP_PROFILE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_LEASE_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_LEASE_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ISSUE_LEASE_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ACKNOWLEDGE_LEASE_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.SIGN_LEASE_DOCUMENT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_LEASE_DOCUMENT_TEMPLATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_ESTATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_ESTATE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.CREATE_SERVICE_CHARGE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SERVICE_CHARGE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_SALE_PIPELINE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_SALE_PIPELINE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ACCEPT_SALE_OFFER;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_WEALTH;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_WEALTH_ASSETS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_WEALTH_FINANCE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_WEALTH_COMPLIANCE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_WEALTH_VAULT;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_WEALTH_GOALS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_GATE_DEVICES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_GATE_EVENTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REVIEW_INSURANCE_APPLICATIONS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_QUOTES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_CLAIMS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_RENEWALS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_CATALOG;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_INSURANCE_PAYMENT_CONFIG;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VERIFY_INSURANCE_PAYMENTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.ISSUE_INSURANCE_POLICIES;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_INSURANCE_REPORTS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.VIEW_COMMUNITY_FUNDS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.MANAGE_COMMUNITY_FUNDS;
import static org.pms.silverocean.service.auth.roles.enums.Permission.REQUEST_FUND_EXPENDITURE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.APPROVE_FUND_EXPENDITURE;
import static org.pms.silverocean.service.auth.roles.enums.Permission.RECORD_FUND_DISBURSEMENT;

@Getter
public enum PMSPermission {
    LANDLORD_PERMISSIONS(PMSRole.LANDLORD, Set.of(CREATE_PROPERTY, EDIT_PROPERTY, VIEW_PROPERTY, DELETE_PROPERTY,
            CREATE_UNIT, EDIT_UNIT, VIEW_UNIT, DELETE_UNIT, ADVERTISE_UNIT,
            CREATE_PARAM, VIEW_PARAM, EDIT_PARAM, DELETE_PARAM, DELETE_PROPERTY_PARAM, EDIT_PROPERTY_PARAM, VIEW_PROPERTY_PARAM,
            CREATE_INVITE, SHARE_INVITE, VIEW_LEASE_INVITE, VIEW_INVITE_LIST, UPDATE_INVITE,
            EDIT_UNIT_CHARGES, VIEW_UNIT_CHARGES,
            VIEW_PROPERTY_STAFF, DELETE_PROPERTY_STAFF,
            VIEW_LEASE_TEMPLATE, EDIT_LEASE_TEMPLATE, CREATE_LEASE_TEMPLATE, DELETE_LEASE_TEMPLATE, LIST_LEASE_TEMPLATE,
            CREATE_LEASE_DOCUMENT, VIEW_LEASE_DOCUMENT, ISSUE_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT,
            VIEW_ACTIVE_LEASE, VIEW_TENANTS, SIGN_LEASE, SEND_LEASE_MESSAGE, VIEW_LEASE_MESSAGE, EDIT_LEASE,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, VIEW_UNIT_LIST, VIEW_PROPERTY_LIST, RECORD_MANUAL_PAYMENT,VIEW_SUBSCRIPTION_PLAN,
            VIEW_VISITOR_LIST, MANAGE_GATE_DEVICES, VIEW_GATE_EVENTS,
            CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT, DECRYPT_ACCOUNT_PROPERTY,
            VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, VIEW_SP_RATINGS, CREATE_SP_BOOKING, CANCEL_SP_BOOKING, VIEW_SP_BOOKING, RATE_SP_SERVICE, FILE_SP_COMPLAINT,
            VIEW_WEALTH, MANAGE_WEALTH_ASSETS, MANAGE_WEALTH_FINANCE, MANAGE_WEALTH_COMPLIANCE, MANAGE_WEALTH_VAULT, MANAGE_WEALTH_GOALS,
            VIEW_COMMUNITY_FUNDS, MANAGE_COMMUNITY_FUNDS, REQUEST_FUND_EXPENDITURE,
            APPROVE_FUND_EXPENDITURE, RECORD_FUND_DISBURSEMENT)),
    SERVICE_PROVIDER(PMSRole.SERVICE_PROVIDER, Set.of(
            SETUP_SP_PROFILE, VIEW_SP_PROFILE, EDIT_SP_PROFILE,
            VIEW_SP_CATEGORY_LIST, ADD_SP_SERVICE, EDIT_SP_SERVICE, REMOVE_SP_SERVICE, VIEW_SP_SERVICE,
            UPLOAD_SP_DOCUMENT, ADD_SP_REFEREE, EDIT_SP_REFEREE, REMOVE_SP_REFEREE,
            CONFIRM_SP_BOOKING, COMPLETE_SP_BOOKING, CANCEL_SP_BOOKING, VIEW_SP_BOOKING,
            VIEW_SP_RATINGS, FILE_SP_COMPLAINT,
            CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT, DECRYPT_ACCOUNT_PROPERTY)),
    ASSET_PORTFOLIO_MANAGER(PMSRole.ASSET_PORTFOLIO_MANAGER, Set.of(VIEW_WEALTH, MANAGE_WEALTH_ASSETS,
            MANAGE_WEALTH_FINANCE, MANAGE_WEALTH_COMPLIANCE, MANAGE_WEALTH_VAULT, MANAGE_WEALTH_GOALS)),
    AFFILIATE(PMSRole.AFFILIATE, Set.of(CREATE_INVITE, SHARE_INVITE, VIEW_INVITE_LIST, UPDATE_INVITE,
            CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT, DECRYPT_ACCOUNT_PROPERTY)),
    TENANT(PMSRole.TENANT, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_UNIT_CHARGES,
            VIEW_UNIT_LEASE, VIEW_LEASE_TEMPLATE, CREATE_NEW_LEASE,
            VIEW_LANDLORD_AND_MANAGERS,
            VIEW_ACTIVE_LEASE, SIGN_LEASE, SEND_LEASE_MESSAGE, VIEW_LEASE_MESSAGE, EDIT_LEASE,
            VIEW_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, VIEW_UNIT_LIST, VIEW_PROPERTY_LIST,
            VIEW_VISITOR_LIST, REGISTER_VISITOR, CANCEL_VISITOR, DELETE_VISITOR, VIEW_ACCOUNT,
            VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, VIEW_SP_RATINGS, CREATE_SP_BOOKING, CANCEL_SP_BOOKING, VIEW_SP_BOOKING, RATE_SP_SERVICE, FILE_SP_COMPLAINT,
            VIEW_COMMUNITY_FUNDS)),
    PROPERTY_MANAGER(PMSRole.PROPERTY_MANAGER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_PARAM, VIEW_UNIT_CHARGES,
            VIEW_LEASE_TEMPLATE, VIEW_ACTIVE_LEASE, VIEW_TENANTS, SIGN_LEASE, SEND_LEASE_MESSAGE, VIEW_LEASE_MESSAGE, EDIT_LEASE,
            CREATE_LEASE_DOCUMENT, VIEW_LEASE_DOCUMENT, ISSUE_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, RECORD_MANUAL_PAYMENT,
            VIEW_VISITOR_LIST, MANAGE_GATE_DEVICES, VIEW_GATE_EVENTS,
            VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, VIEW_SP_RATINGS, CREATE_SP_BOOKING, CANCEL_SP_BOOKING, VIEW_SP_BOOKING, RATE_SP_SERVICE, FILE_SP_COMPLAINT
    )),
    WORKSPACE_ADMIN(PMSRole.WORKSPACE_ADMIN, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_PROPERTY_PARAM, VIEW_UNIT_CHARGES, VIEW_PROPERTY_STAFF, VIEW_TENANTS,
            VIEW_LEASE_TEMPLATE, VIEW_ACTIVE_LEASE, VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST,
            VIEW_VISITOR_LIST, VIEW_GATE_EVENTS, VIEW_ESTATE, VIEW_SERVICE_CHARGE, VIEW_SALE_PIPELINE)),
    PROPERTY_ACCOUNTANT(PMSRole.PROPERTY_ACCOUNTANT, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, RECORD_MANUAL_PAYMENT, VIEW_SERVICE_CHARGE)),
    LEASING_OFFICER(PMSRole.LEASING_OFFICER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_LEASE_TEMPLATE, VIEW_ACTIVE_LEASE, VIEW_TENANTS, SEND_LEASE_MESSAGE, VIEW_LEASE_MESSAGE,
            CREATE_LEASE_DOCUMENT, VIEW_LEASE_DOCUMENT, ISSUE_LEASE_DOCUMENT)),
    ESTATE_OPERATIONS_MANAGER(PMSRole.ESTATE_OPERATIONS_MANAGER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST,
            VIEW_UNIT_LIST, VIEW_ESTATE, MANAGE_ESTATE, CREATE_SERVICE_CHARGE, VIEW_SERVICE_CHARGE,
            VIEW_COMMUNITY_FUNDS, MANAGE_COMMUNITY_FUNDS, VIEW_VISITOR_LIST, VIEW_GATE_EVENTS)),
    SECURITY_SUPERVISOR(PMSRole.SECURITY_SUPERVISOR, Set.of(VIEW_PROPERTY, VIEW_PROPERTY_LIST,
            VIEW_VISITOR_LIST, MANAGE_GATE_DEVICES, VIEW_GATE_EVENTS, UPDATE_VISITOR_STATUS)),
    SALES_COORDINATOR(PMSRole.SALES_COORDINATOR, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_SALE_PIPELINE, MANAGE_SALE_PIPELINE, VIEW_LEASE_DOCUMENT)),
    LISTING_AGENT(PMSRole.LISTING_AGENT, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_SALE_PIPELINE, MANAGE_SALE_PIPELINE, VIEW_LEASE_DOCUMENT)),
    WORKSPACE_VIEWER(PMSRole.WORKSPACE_VIEWER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, VIEW_ESTATE, VIEW_SERVICE_CHARGE,
            VIEW_SALE_PIPELINE, VIEW_VISITOR_LIST, VIEW_GATE_EVENTS)),
    ESTATE_MANAGER(PMSRole.ESTATE_MANAGER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_ESTATE, MANAGE_ESTATE, CREATE_SERVICE_CHARGE, VIEW_SERVICE_CHARGE,
            CREATE_INVITE, SHARE_INVITE, VIEW_INVITE_LIST, UPDATE_INVITE,
            VIEW_COMMUNITY_FUNDS, MANAGE_COMMUNITY_FUNDS, REQUEST_FUND_EXPENDITURE,
            APPROVE_FUND_EXPENDITURE, RECORD_FUND_DISBURSEMENT,
            CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT, DECRYPT_ACCOUNT_PROPERTY,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST,
            CREATE_LEASE_DOCUMENT, VIEW_LEASE_DOCUMENT, ISSUE_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT,
            VIEW_VISITOR_LIST, MANAGE_GATE_DEVICES, VIEW_GATE_EVENTS, VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, CREATE_SP_BOOKING, VIEW_SP_BOOKING)),
    HOMEOWNER(PMSRole.HOMEOWNER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_ESTATE, VIEW_SERVICE_CHARGE, VIEW_COMMUNITY_FUNDS, VIEW_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT,
            VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, VIEW_MY_NOTIFICATIONS,
            VIEW_VISITOR_LIST, REGISTER_VISITOR, CANCEL_VISITOR, VIEW_SP_CATEGORY_LIST, VIEW_SP_SERVICE, CREATE_SP_BOOKING, VIEW_SP_BOOKING,
            VIEW_WEALTH, MANAGE_WEALTH_ASSETS, MANAGE_WEALTH_FINANCE, MANAGE_WEALTH_COMPLIANCE, MANAGE_WEALTH_VAULT, MANAGE_WEALTH_GOALS)),
    SALES_AGENT(PMSRole.SALES_AGENT, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_SALE_PIPELINE, MANAGE_SALE_PIPELINE, CREATE_LEASE_DOCUMENT, VIEW_LEASE_DOCUMENT,
            ISSUE_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT)),
    BUYER(PMSRole.BUYER, Set.of(VIEW_PROPERTY, VIEW_UNIT, VIEW_PROPERTY_LIST, VIEW_UNIT_LIST,
            VIEW_SALE_PIPELINE, ACCEPT_SALE_OFFER, VIEW_LEASE_DOCUMENT, ACKNOWLEDGE_LEASE_DOCUMENT, SIGN_LEASE_DOCUMENT)),
    INSURANCE_ADVISER(PMSRole.INSURANCE_ADVISER, Set.of(REVIEW_INSURANCE_APPLICATIONS, MANAGE_INSURANCE_QUOTES,
            MANAGE_INSURANCE_CLAIMS, MANAGE_INSURANCE_RENEWALS)),
    INSURANCE_MANAGER(PMSRole.INSURANCE_MANAGER, Set.of(REVIEW_INSURANCE_APPLICATIONS, MANAGE_INSURANCE_QUOTES,
            MANAGE_INSURANCE_CLAIMS, MANAGE_INSURANCE_RENEWALS, MANAGE_INSURANCE_CATALOG,
            MANAGE_INSURANCE_PAYMENT_CONFIG, VERIFY_INSURANCE_PAYMENTS, ISSUE_INSURANCE_POLICIES,
            VIEW_INSURANCE_REPORTS, CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT,
            DECRYPT_ACCOUNT_PROPERTY)),
    SUPPORT(PMSRole.SUPPORT, Set.of(LIST_USERS, VIEW_NOTIFICATIONS)),
    SALES_MARKETING(PMSRole.SALES_MARKETING, Set.of(VIEW_PROPERTY_LIST, VIEW_SP_LIST, VIEW_SUBSCRIPTION_PLAN)),
    FINANCE(PMSRole.FINANCE, Set.of(VIEW_INVOICE_LIST, VIEW_INVOICE_PDF, VIEW_PAYMENT_LIST, VIEW_UNIT_LIST, VIEW_PROPERTY_LIST)),
    GUARD(PMSRole.GUARD, Set.of(VIEW_VISITOR_LIST, UPDATE_VISITOR_STATUS, VIEW_GATE_EVENTS)),
    SUPER_ADMIN(PMSRole.SUPER_ADMIN, Set.of(LIST_USERS, MANAGE_INTERNAL_STAFF, VIEW_AUDIT_LOGS, VIEW_CONFIG, EDIT_CONFIG, VERIFY_PARAM,
            VIEW_ALL_PARAMS, VIEW_NOTIFICATIONS, ROTATE_KEY, VIEW_INVOICE_LIST, VIEW_INVOICE_PDF,
            VIEW_PROPERTY_LANDLORDS, VIEW_PROPERTY_TENANTS, VIEW_UNIT_LIST, VIEW_PROPERTY_LIST,
            CREATE_SUBSCRIPTION_PLAN, EDIT_SUBSCRIPTION_PLAN, VIEW_SUBSCRIPTION_PLAN,
            CREATE_ACCOUNT, EDIT_ACCOUNT, VIEW_ACCOUNT, DELETE_ACCOUNT, DECRYPT_ACCOUNT_PROPERTY, VIEW_ALL_ACCOUNTS, VERIFY_ACCOUNT,
            VIEW_SP_LIST, APPROVE_SP_SERVICE, SUSPEND_SP_SERVICE, ASSIGN_SP_TIER, VIEW_SP_DOCUMENT, VERIFY_SP_DOCUMENT,
            VIEW_LEASE_DOCUMENT, MANAGE_LEASE_DOCUMENT_TEMPLATE,
            VERIFY_SP_REFEREE, REVIEW_SP_COMPLAINT, RESOLVE_SP_COMPLAINT, BLACKLIST_SP, MANAGE_SP_CATEGORIES, VIEW_SP_PROFILE,
            VIEW_WEALTH, MANAGE_WEALTH_ASSETS, MANAGE_WEALTH_FINANCE, MANAGE_WEALTH_COMPLIANCE, MANAGE_WEALTH_VAULT, MANAGE_WEALTH_GOALS,
            MANAGE_GATE_DEVICES, VIEW_GATE_EVENTS)),
    ;

    private final PMSRole role;
    private final Set<String> permissions;

    PMSPermission(PMSRole role, Set<String> permissions) {
        this.role = role;
        this.permissions = permissions;
    }
}
