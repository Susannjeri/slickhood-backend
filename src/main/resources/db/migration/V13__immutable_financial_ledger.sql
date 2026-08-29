CREATE TABLE pms_financial_journal (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 event_key VARCHAR(190) NOT NULL, event_type VARCHAR(50) NOT NULL,
 source_type VARCHAR(50) NOT NULL, source_id VARCHAR(120) NOT NULL,
 provider_reference VARCHAR(120), occurred_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_financial_journal_uuid (uuid),
 UNIQUE KEY uk_financial_journal_event_key (event_key),
 KEY idx_financial_journal_source (source_type, source_id), KEY idx_financial_journal_occurred (occurred_at)
);

CREATE TABLE pms_financial_ledger_line (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 journal_id BIGINT NOT NULL, line_number INT NOT NULL, account_code VARCHAR(50) NOT NULL,
 user_id BIGINT, property_id BIGINT, unit_id BIGINT, currency VARCHAR(12) NOT NULL,
 debit DECIMAL(19,2) NOT NULL DEFAULT 0.00, credit DECIMAL(19,2) NOT NULL DEFAULT 0.00,
 description VARCHAR(255), PRIMARY KEY (id), UNIQUE KEY uk_financial_ledger_line_uuid (uuid),
 UNIQUE KEY uk_financial_ledger_line_number (journal_id, line_number),
 KEY idx_financial_ledger_user_date (user_id, created_on),
 KEY idx_financial_ledger_property_date (property_id, created_on),
 CONSTRAINT fk_financial_ledger_journal FOREIGN KEY (journal_id) REFERENCES pms_financial_journal(id),
 CONSTRAINT chk_financial_ledger_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);
