package org.pms.silverocean.service.auth.roles.enums;

public class Permission {
    public static final String LIST_USERS = "list_users";
    public static final String MANAGE_INTERNAL_STAFF = "manage_internal_staff";
    public static final String CREATE_PROPERTY = "create_property";
    public static final String EDIT_PROPERTY = "edit_property";
    public static final String DELETE_PROPERTY = "delete_property";
    public static final String VIEW_PROPERTY = "view_property";

    public static final String ASSIGN_ROLE = "assign_role";

    public static final String CREATE_UNIT = "create_unit";
    public static final String EDIT_UNIT = "edit_unit";
    public static final String VIEW_UNIT = "view_unit";
    public static final String DELETE_UNIT = "delete_unit";
    public static final String DUPLICATE_UNIT = "create_similar_unit";
    public static final String MARK_OCCUPIED_UNIT = "mark_occupied";
    public static final String ADVERTISE_UNIT = "advertise_unit";

    public static final String VIEW_AUDIT_LOGS = "view_audit_logs";

    public static final String EDIT_PARAM = "edit_param";
    public static final String CREATE_PARAM = "create_param";
    public static final String DELETE_PARAM = "delete_param";
    public static final String VIEW_PARAM = "view_param";
    public static final String VIEW_ALL_PARAMS = "view_all_params";
    public static final String VERIFY_PARAM = "verify_param";

    public static final String EDIT_CONFIG = "edit_config";
    public static final String VIEW_CONFIG = "view_config";

    public static final String DELETE_PROPERTY_PARAM = "delete_property_param";
    public static final String EDIT_PROPERTY_PARAM = "edit_property_param";
    public static final String VIEW_PROPERTY_PARAM = "view_property_param";


    public static final String VIEW_PROPERTY_STAFF = "view_property_staff";
    public static final String DELETE_PROPERTY_STAFF = "delete_property_staff";
    public static final String VIEW_PROPERTY_LANDLORDS = "view_property_landlords";
    public static final String VIEW_PROPERTY_TENANTS = "view_property_tenants";
    public static final String VIEW_UNIT_LIST = "view_unit_list";
    public static final String VIEW_PROPERTY_LIST = "view_property_list";


    public static final String VIEW_NOTIFICATIONS = "view_notifications";
    public static final String VIEW_MY_NOTIFICATIONS = "view_my_notifications";
    public static final String ROTATE_KEY = "rotate_key";

    public static final String CREATE_INVITE = "create_invite";
    public static final String UPDATE_INVITE = "update_invite";
    public static final String VIEW_LEASE_INVITE = "view_lease_invite";
    public static final String VIEW_INVITE_LIST = "view_invite_list";
    public static final String SHARE_INVITE = "share_invite";
    public static final String CREATE_LEASE = "create_lease";
    public static final String DELETE_LEASE = "delete_lease";
    public static final String EDIT_UNIT_CHARGES = "edit_unit_charges";
    public static final String VIEW_UNIT_CHARGES = "view_unit_charges";
    public static final String CREATE_INVOICE = "create_invoice";


    public static final String VIEW_UNIT_LEASE = "view_unit_lease";
    public static final String EDIT_LEASE_CHARGES = "edit_lease_charges";
    public static final String CREATE_NEW_LEASE = "create_new_lease";
    public static final String EDIT_LEASE = "edit_lease";
    public static final String VIEW_ACTIVE_LEASE = "view_active_lease";


    public static final String CREATE_LEASE_TEMPLATE = "create_lease_template";
    public static final String EDIT_LEASE_TEMPLATE = "edit_lease_template";
    public static final String DELETE_LEASE_TEMPLATE = "delete_lease_template";
    public static final String VIEW_LEASE_TEMPLATE = "view_lease_template";
    public static final String LIST_LEASE_TEMPLATE = "list_lease_template";

    public static final String VIEW_TENANTS = "view_tenants";
    public static final String DELETE_TENANT = "delete_tenant";
    public static final String VIEW_LANDLORD_AND_MANAGERS = "view_landlord_and_managers";
    public static final String SIGN_LEASE = "sign_lease";
    public static final String SEND_LEASE_MESSAGE = "send_lease_message";
    public static final String VIEW_LEASE_MESSAGE = "view_lease_message";
    public static final String CREATE_LEASE_DOCUMENT = "create_lease_document";
    public static final String VIEW_LEASE_DOCUMENT = "view_lease_document";
    public static final String ISSUE_LEASE_DOCUMENT = "issue_lease_document";
    public static final String ACKNOWLEDGE_LEASE_DOCUMENT = "acknowledge_lease_document";
    public static final String SIGN_LEASE_DOCUMENT = "sign_lease_document";
    public static final String MANAGE_LEASE_DOCUMENT_TEMPLATE = "manage_lease_document_template";
    public static final String VIEW_ESTATE = "view_estate";
    public static final String MANAGE_ESTATE = "manage_estate";
    public static final String CREATE_SERVICE_CHARGE = "create_service_charge";
    public static final String VIEW_SERVICE_CHARGE = "view_service_charge";
    public static final String VIEW_SALE_PIPELINE = "view_sale_pipeline";
    public static final String MANAGE_SALE_PIPELINE = "manage_sale_pipeline";
    public static final String ACCEPT_SALE_OFFER = "accept_sale_offer";

    public static final String VIEW_WEALTH = "view_wealth";
    public static final String MANAGE_WEALTH_ASSETS = "manage_wealth_assets";
    public static final String MANAGE_WEALTH_FINANCE = "manage_wealth_finance";
    public static final String MANAGE_WEALTH_COMPLIANCE = "manage_wealth_compliance";
    public static final String MANAGE_WEALTH_VAULT = "manage_wealth_vault";
    public static final String MANAGE_WEALTH_GOALS = "manage_wealth_goals";


    public static final String VIEW_INVOICE_LIST = "view_invoice_list";
    public static final String VIEW_INVOICE_PDF = "view_invoice_pdf";
    public static final String VIEW_PAYMENT_LIST = "view_payment_list";

    public static final String RECORD_MANUAL_PAYMENT = "record_manual_payment";

    public static final String CREATE_SUBSCRIPTION_PLAN = "create_subscription_plan";
    public static final String EDIT_SUBSCRIPTION_PLAN = "edit_subscription_plan";
    public static final String VIEW_SUBSCRIPTION_PLAN = "view_subscription_plan";

    public static final String REGISTER_VISITOR = "register_visitor";
    public static final String VIEW_VISITOR_LIST = "view_visitor_list";
    public static final String UPDATE_VISITOR_STATUS = "update_visitor_status";
    public static final String CANCEL_VISITOR = "cancel_visitor";
    public static final String DELETE_VISITOR = "delete_visitor";
    public static final String MANAGE_GATE_DEVICES = "manage_gate_devices";
    public static final String VIEW_GATE_EVENTS = "view_gate_events";

    public static final String CREATE_ACCOUNT           = "create_account";
    public static final String EDIT_ACCOUNT             = "edit_account";
    public static final String VIEW_ACCOUNT             = "view_account";
    public static final String VIEW_ALL_ACCOUNTS        = "view_all_accounts";
    public static final String DELETE_ACCOUNT           = "delete_account";
    public static final String DECRYPT_ACCOUNT_PROPERTY = "decrypt_account_property";
    public static final String VERIFY_ACCOUNT = "verify_account";

    // Service Provider
    public static final String SETUP_SP_PROFILE       = "setup_sp_profile";
    public static final String VIEW_SP_PROFILE         = "view_sp_profile";
    public static final String EDIT_SP_PROFILE         = "edit_sp_profile";
    public static final String MANAGE_SP_CATEGORIES    = "manage_sp_categories";
    public static final String VIEW_SP_CATEGORY_LIST   = "view_sp_category_list";
    public static final String ADD_SP_SERVICE          = "add_sp_service";
    public static final String EDIT_SP_SERVICE         = "edit_sp_service";
    public static final String REMOVE_SP_SERVICE       = "remove_sp_service";
    public static final String VIEW_SP_SERVICE         = "view_sp_service";
    public static final String APPROVE_SP_SERVICE      = "approve_sp_service";
    public static final String SUSPEND_SP_SERVICE      = "suspend_sp_service";
    public static final String ASSIGN_SP_TIER          = "assign_sp_tier";
    public static final String UPLOAD_SP_DOCUMENT      = "upload_sp_document";
    public static final String VIEW_SP_DOCUMENT        = "view_sp_document";
    public static final String VERIFY_SP_DOCUMENT      = "verify_sp_document";
    public static final String ADD_SP_REFEREE          = "add_sp_referee";
    public static final String EDIT_SP_REFEREE         = "edit_sp_referee";
    public static final String REMOVE_SP_REFEREE       = "remove_sp_referee";
    public static final String VERIFY_SP_REFEREE       = "verify_sp_referee";
    public static final String CREATE_SP_BOOKING       = "create_sp_booking";
    public static final String CONFIRM_SP_BOOKING      = "confirm_sp_booking";
    public static final String COMPLETE_SP_BOOKING     = "complete_sp_booking";
    public static final String CANCEL_SP_BOOKING       = "cancel_sp_booking";
    public static final String VIEW_SP_BOOKING         = "view_sp_booking";
    public static final String RATE_SP_SERVICE         = "rate_sp_service";
    public static final String VIEW_SP_RATINGS         = "view_sp_ratings";
    public static final String FILE_SP_COMPLAINT       = "file_sp_complaint";
    public static final String REVIEW_SP_COMPLAINT     = "review_sp_complaint";
    public static final String RESOLVE_SP_COMPLAINT    = "resolve_sp_complaint";
    public static final String VIEW_SP_LIST            = "view_sp_list";
    public static final String BLACKLIST_SP            = "blacklist_sp";

    // Insurance Hub - customer access is authenticated and owner-scoped; these are Silverwood operations only.
    public static final String REVIEW_INSURANCE_APPLICATIONS = "review_insurance_applications";
    public static final String MANAGE_INSURANCE_QUOTES = "manage_insurance_quotes";
    public static final String APPROVE_INSURANCE_QUOTES = "approve_insurance_quotes";
    public static final String MANAGE_INSURANCE_CLAIMS = "manage_insurance_claims";
    public static final String MANAGE_INSURANCE_RENEWALS = "manage_insurance_renewals";
    public static final String MANAGE_INSURANCE_CATALOG = "manage_insurance_catalog";
    public static final String MANAGE_INSURANCE_PAYMENT_CONFIG = "manage_insurance_payment_config";
    public static final String VERIFY_INSURANCE_PAYMENTS = "verify_insurance_payments";
    public static final String ISSUE_INSURANCE_POLICIES = "issue_insurance_policies";
    public static final String VIEW_INSURANCE_REPORTS = "view_insurance_reports";

    // Community welfare, project, reserve and emergency funds.
    public static final String VIEW_COMMUNITY_FUNDS = "view_community_funds";
    public static final String MANAGE_COMMUNITY_FUNDS = "manage_community_funds";
    public static final String REQUEST_FUND_EXPENDITURE = "request_fund_expenditure";
    public static final String APPROVE_FUND_EXPENDITURE = "approve_fund_expenditure";
    public static final String RECORD_FUND_DISBURSEMENT = "record_fund_disbursement";
}
