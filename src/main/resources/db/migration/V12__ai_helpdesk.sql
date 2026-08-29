CREATE TABLE pms_help_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), user_id BIGINT NOT NULL, active_role VARCHAR(60) NOT NULL,
    subject VARCHAR(180) NOT NULL, status VARCHAR(30) NOT NULL, priority VARCHAR(20) NOT NULL,
    assigned_to_user_id BIGINT, last_message_at DATETIME(6), escalated_at DATETIME(6), resolved_at DATETIME(6),
    PRIMARY KEY (id), UNIQUE KEY uk_help_conversation_uuid (uuid),
    KEY idx_help_conversation_user (user_id, last_message_at), KEY idx_help_conversation_queue (status, priority, last_message_at),
    CONSTRAINT fk_help_conversation_user FOREIGN KEY (user_id) REFERENCES pms_users(id),
    CONSTRAINT fk_help_conversation_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES pms_users(id)
);

CREATE TABLE pms_help_message (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), conversation_id BIGINT NOT NULL, sender_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL, model VARCHAR(80), provider_response_id VARCHAR(120), source_article_ids VARCHAR(500),
    PRIMARY KEY (id), UNIQUE KEY uk_help_message_uuid (uuid), KEY idx_help_message_conversation (conversation_id, created_on),
    CONSTRAINT fk_help_message_conversation FOREIGN KEY (conversation_id) REFERENCES pms_help_conversation(id)
);

CREATE TABLE pms_help_article (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    created_by BIGINT, last_modified_date DATETIME(6), slug VARCHAR(160) NOT NULL, title VARCHAR(200) NOT NULL,
    category VARCHAR(80) NOT NULL, body TEXT NOT NULL, keywords VARCHAR(500), audience_roles VARCHAR(500), published BIT NOT NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_help_article_uuid (uuid), UNIQUE KEY uk_help_article_slug (slug),
    KEY idx_help_article_category (category, published, active)
);

INSERT INTO pms_help_article
(uuid, created_on, active, created_by, last_modified_date, slug, title, category, body, keywords, audience_roles, published)
VALUES
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'registration-and-roles', 'Registration, roles and changing role', 'Getting started',
 'Register one SlickHood account, verify it, and then select the role you want to use. A user may hold more than one role. Use Change role in the account navigation to switch the active workspace; permissions and dashboard content follow the active role.',
 'register registration role change switch account verification', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'subscriptions-and-trial', 'Subscriptions and the free trial', 'Subscriptions',
 'Registration happens before subscription selection. Eligible plans start with the configured free-trial period, currently 14 days. Landlord, estate and property-sale business areas use Bronze, Silver, Gold and Platinum tiers. Plan limits and prices are maintained by authorised administrators.',
 'subscription trial bronze silver gold platinum upgrade units', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'rent-and-payment-support', 'Rent payments, confirmations and receipts', 'Payments',
 'Use the invoice reference supplied by SlickHood when paying. A payment is complete only after the provider callback is reconciled to the invoice. Never send card details, PINs, OTPs or mobile-money credentials to support. If a payment is missing, provide only the invoice reference, amount, channel and approximate payment time and escalate to a human agent.',
 'rent payment mpesa paystack card receipt invoice reconciliation', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'tenant-and-lease-help', 'Tenant onboarding, leases and notices', 'Property management',
 'Tenants join through an invitation, complete the required profile and KYC steps, review the lease, and sign or acknowledge documents according to the document workflow. Landlords and managers issue governed notices from Documents and Notices so delivery and acknowledgement remain auditable.',
 'tenant invite onboarding lease sign notice documents landlord', 'Tenant,Landlord,PropertyManager', 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'visitors-and-smart-gates', 'Visitors, deliveries and smart gates', 'Visitors',
 'The visitor module supports walk-ins, drive-ins and deliveries. Residents or authorised staff create or approve access, while guards record gate events. Smart-gate devices use the same access decision and audit trail. Do not share permanent access credentials in help-desk messages.',
 'visitor walk in drive delivery guard gate smart access code', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'marketplace-and-soko', 'Marketplace services and Soko orders', 'Marketplace',
 'Marketplace connects users with nearby verified service providers. Soko lists nearby grocery shops and supports shop-managed preferred delivery riders and delivery-code confirmation. Service disputes remain in the marketplace complaint process; general product assistance belongs in the Help Desk.',
 'marketplace service provider soko grocery rider delivery code complaint', NULL, 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'wealth-overview', 'Using SlickHood Wealth', 'Wealth',
 'Wealth is the owner financial command centre for assets, valuations, acquisition costs, income, expenses, yields, mortgages, debt, equity, compliance obligations, documents and goals. Values are decision-support information and should be checked before legal, tax or investment decisions.',
 'wealth portfolio asset valuation yield mortgage equity compliance vault', 'Landlord,AssetPortfolioManager,Homeowner', 1),
(UUID_TO_BIN(UUID()), NOW(6), 1, NULL, NOW(6), 'kyc-document-safety', 'KYC document upload and safety', 'Account security',
 'KYC is upload-first: submit clear supported documents and complete consent and phone verification. Do not type identity-document numbers, passwords, OTPs, payment PINs or full card details into the Help Desk. KYC approval and registry checks must be handled by authorised reviewers.',
 'kyc identity document upload ocr verification privacy security', NULL, 1);
