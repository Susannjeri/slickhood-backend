CREATE TABLE pms_customer_workspace (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), owner_user_id BIGINT NOT NULL,
    business_area VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_customer_workspace_uuid (uuid),
    UNIQUE KEY uk_customer_workspace_owner_area (owner_user_id, business_area),
    KEY idx_customer_workspace_owner (owner_user_id, active)
);

CREATE TABLE pms_team_role_definition (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), code VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL, description VARCHAR(300), business_area VARCHAR(40) NOT NULL,
    permission_template VARCHAR(40) NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_team_role_definition_uuid (uuid),
    UNIQUE KEY uk_team_role_definition_code (code), KEY idx_team_role_definition_area (business_area, active)
);

CREATE TABLE pms_workspace_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), workspace_id BIGINT NOT NULL,
    recipient_email VARCHAR(254) NOT NULL, role_definition_id BIGINT NOT NULL, membership_role VARCHAR(40) NOT NULL,
    scope_type VARCHAR(30) NOT NULL, resource_ids_json TEXT, token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL, expires_at DATETIME(6) NOT NULL, accepted_at DATETIME(6),
    last_sent_at DATETIME(6), resend_count INT NOT NULL DEFAULT 0, membership_id BIGINT,
    PRIMARY KEY (id), UNIQUE KEY uk_workspace_invitation_uuid (uuid),
    UNIQUE KEY uk_workspace_invite_token_hash (token_hash),
    KEY idx_workspace_invite_workspace_status (workspace_id, status, active),
    KEY idx_workspace_invite_email (recipient_email, status),
    CONSTRAINT fk_workspace_invite_workspace FOREIGN KEY (workspace_id) REFERENCES pms_customer_workspace(id),
    CONSTRAINT fk_workspace_invite_role FOREIGN KEY (role_definition_id) REFERENCES pms_team_role_definition(id)
);

CREATE TABLE pms_workspace_membership (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), workspace_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    member_email VARCHAR(254) NOT NULL, role_definition_id BIGINT NOT NULL, membership_role VARCHAR(40) NOT NULL,
    scope_type VARCHAR(30) NOT NULL, resource_ids_json TEXT, status VARCHAR(30) NOT NULL,
    accepted_at DATETIME(6), activated_at DATETIME(6), suspended_at DATETIME(6), revoked_at DATETIME(6),
    PRIMARY KEY (id), UNIQUE KEY uk_workspace_membership_uuid (uuid),
    UNIQUE KEY uk_workspace_membership_user (workspace_id, user_id),
    KEY idx_workspace_membership_workspace_status (workspace_id, status, active),
    KEY idx_workspace_membership_user (user_id, status, active),
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES pms_customer_workspace(id),
    CONSTRAINT fk_workspace_member_user FOREIGN KEY (user_id) REFERENCES pms_users(id),
    CONSTRAINT fk_workspace_member_role FOREIGN KEY (role_definition_id) REFERENCES pms_team_role_definition(id)
);

INSERT INTO pms_team_role_definition
    (uuid, created_on, active, created_by, last_modified_date, code, display_name, description, business_area, permission_template)
VALUES
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'LANDLORD_WORKSPACE_ADMIN','Workspace administrator','Delegated workspace administration','LANDLORD','WORKSPACE_ADMIN'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'LANDLORD_PROPERTY_MANAGER','Property manager','Property and tenancy operations','LANDLORD','PROPERTY_MANAGER'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'LANDLORD_PROPERTY_ACCOUNTANT','Property accountant','Customer-side property accounting','LANDLORD','PROPERTY_ACCOUNTANT'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'LANDLORD_LEASING_OFFICER','Leasing officer','Lease and tenant administration','LANDLORD','LEASING_OFFICER'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'LANDLORD_VIEWER','Viewer','Read-only workspace access','LANDLORD','VIEWER'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_WORKSPACE_ADMIN','Workspace administrator','Delegated workspace administration','ESTATE_MANAGEMENT','WORKSPACE_ADMIN'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_PROPERTY_ACCOUNTANT','Property accountant','Customer-side estate accounting','ESTATE_MANAGEMENT','PROPERTY_ACCOUNTANT'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_OPERATIONS_MANAGER','Estate operations manager','Estate operations and community administration','ESTATE_MANAGEMENT','ESTATE_OPERATIONS_MANAGER'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_SECURITY_SUPERVISOR','Security supervisor','Security and smart-gate supervision','ESTATE_MANAGEMENT','SECURITY_SUPERVISOR'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_GUARD','Guard','Scoped live gate operations','ESTATE_MANAGEMENT','GUARD'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'ESTATE_VIEWER','Viewer','Read-only workspace access','ESTATE_MANAGEMENT','VIEWER'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'SALE_WORKSPACE_ADMIN','Workspace administrator','Delegated workspace administration','PROPERTY_SALE_MANAGEMENT','WORKSPACE_ADMIN'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'SALE_PROPERTY_ACCOUNTANT','Property accountant','Customer-side sales accounting','PROPERTY_SALE_MANAGEMENT','PROPERTY_ACCOUNTANT'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'SALE_COORDINATOR','Sales coordinator','Sales pipeline coordination','PROPERTY_SALE_MANAGEMENT','SALES_COORDINATOR'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'SALE_LISTING_AGENT','Listing agent','Scoped listing and buyer work','PROPERTY_SALE_MANAGEMENT','LISTING_AGENT'),
    (UUID_TO_BIN(UUID()),NOW(6),1,0,NOW(6),'SALE_VIEWER','Viewer','Read-only workspace access','PROPERTY_SALE_MANAGEMENT','VIEWER');

INSERT INTO pms_plan_quota
    (uuid, created_on, active, created_by, last_modified_date, metric_key, limit_value, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 'TEAM_SEATS',
       CASE WHEN p.code LIKE '%BRONZE%' THEN 2 WHEN p.code LIKE '%SILVER%' THEN 5
            WHEN p.code LIKE '%GOLD%' THEN 15 ELSE -1 END, p.id
FROM pms_subscription_plan p
WHERE p.active = 1 AND p.role_family IN ('LANDLORD','ESTATE_MANAGER','SALES_AGENT')
  AND NOT EXISTS (SELECT 1 FROM pms_plan_quota q WHERE q.subscription_plan_id=p.id AND q.metric_key='TEAM_SEATS' AND q.active=1);
