CREATE TABLE pms_tax_rule_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_code VARCHAR(40) NOT NULL,
    version INT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    rate DECIMAL(12,8) NOT NULL,
    lower_threshold DECIMAL(19,2) NULL,
    upper_threshold DECIMAL(19,2) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    source_url VARCHAR(500) NOT NULL,
    source_note VARCHAR(1000) NOT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME(6) NULL,
    active BIT NOT NULL DEFAULT 1,
    created_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_rule_code_version (rule_code, version),
    KEY idx_tax_rule_effective (rule_code, active, effective_from, effective_to)
);

INSERT INTO pms_tax_rule_version
    (rule_code, version, effective_from, rate, lower_threshold, upper_threshold, source_url, source_note, active)
VALUES
    ('KENYA_MRI', 1, '2024-01-01', 0.07500000, 288000.00, 15000000.00,
     'https://new.kenyalaw.org/akn/ke/act/1973/16/eng@2026-01-01/source',
     'Residential rental income tax: 7.5% of gross residential rent for an eligible resident person. Threshold confirmed against the consolidated Income Tax Act.', 1),
    ('KENYA_CGT_PROPERTY', 1, '2023-01-01', 0.15000000, NULL, NULL,
     'https://www.kra.go.ke/individual/filing-paying/types-of-taxes/capital-gains-tax',
     'Capital gains tax estimate: 15% of the taxable net gain. Exemptions and property-dealer treatment require confirmation.', 1);

CREATE TABLE pms_tax_calculation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    calculation_type VARCHAR(30) NOT NULL,
    rule_version_id BIGINT NOT NULL,
    tax_period VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gross_amount DECIMAL(19,2) NOT NULL,
    taxable_amount DECIMAL(19,2) NOT NULL,
    estimated_tax DECIMAL(19,2) NOT NULL,
    credit_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    estimated_payable DECIMAL(19,2) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    explanation VARCHAR(1500) NOT NULL,
    input_snapshot JSON NOT NULL,
    rule_snapshot JSON NOT NULL,
    due_date DATE NULL,
    created_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tax_calculation_rule FOREIGN KEY (rule_version_id) REFERENCES pms_tax_rule_version(id),
    KEY idx_tax_calculation_owner (owner_user_id, created_on),
    KEY idx_tax_calculation_period (owner_user_id, calculation_type, tax_period)
);

CREATE TABLE pms_tax_connection_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,
    taxpayer_pin_masked VARCHAR(20) NOT NULL,
    requested_scopes VARCHAR(500) NOT NULL,
    consent_version VARCHAR(40) NOT NULL,
    consented_at DATETIME(6) NOT NULL,
    status VARCHAR(40) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_note VARCHAR(1000) NULL,
    active BIT NOT NULL DEFAULT 1,
    created_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_tax_connection_owner (owner_user_id, active, provider),
    KEY idx_tax_connection_status (status, created_on)
);
