ALTER TABLE pms_help_conversation
    MODIFY user_id BIGINT NULL,
    ADD COLUMN ticket_number VARCHAR(24) NULL AFTER user_id,
    ADD COLUMN guest_token_hash CHAR(64) NULL AFTER ticket_number,
    ADD COLUMN guest_expires_at DATETIME(6) NULL AFTER guest_token_hash,
    ADD COLUMN category VARCHAR(60) NOT NULL DEFAULT 'GENERAL' AFTER subject,
    ADD COLUMN page_context VARCHAR(255) NULL AFTER category,
    ADD COLUMN priority_rank INT NOT NULL DEFAULT 2 AFTER priority,
    ADD COLUMN waiting_since DATETIME(6) NULL AFTER last_message_at,
    ADD COLUMN sla_due_at DATETIME(6) NULL AFTER waiting_since,
    ADD COLUMN sla_breached_at DATETIME(6) NULL AFTER sla_due_at,
    ADD COLUMN first_response_at DATETIME(6) NULL AFTER sla_breached_at,
    ADD COLUMN customer_unread_count INT NOT NULL DEFAULT 0 AFTER resolved_at,
    ADD COLUMN agent_unread_count INT NOT NULL DEFAULT 0 AFTER customer_unread_count,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER agent_unread_count;

UPDATE pms_help_conversation
SET ticket_number = CONCAT('SH-', DATE_FORMAT(COALESCE(created_on, NOW()), '%y%m%d'), '-', LPAD(id, 6, '0')),
    waiting_since = COALESCE(escalated_at, last_message_at),
    sla_due_at = CASE WHEN status IN ('ESCALATED', 'ASSIGNED', 'WAITING_FOR_SUPPORT')
        THEN DATE_ADD(COALESCE(escalated_at, last_message_at, NOW()), INTERVAL 4 HOUR) ELSE NULL END,
    priority_rank = CASE priority WHEN 'URGENT' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'LOW' THEN 1 ELSE 2 END
WHERE ticket_number IS NULL;

ALTER TABLE pms_help_conversation
    MODIFY ticket_number VARCHAR(24) NOT NULL,
    ADD UNIQUE KEY uk_help_conversation_ticket (ticket_number),
    ADD KEY idx_help_guest_token (guest_token_hash, guest_expires_at),
    ADD KEY idx_help_queue_sla (status, priority_rank, waiting_since, sla_due_at),
    DROP FOREIGN KEY fk_help_conversation_user,
    ADD CONSTRAINT fk_help_conversation_user FOREIGN KEY (user_id) REFERENCES pms_users(id);

ALTER TABLE pms_help_message
    ADD COLUMN internal_note BIT NOT NULL DEFAULT 0 AFTER source_article_ids,
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER internal_note,
    ADD UNIQUE KEY uk_help_message_idempotency (conversation_id, idempotency_key);

CREATE TABLE pms_help_rate_limit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    active BIT NOT NULL,
    subject_hash CHAR(64) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_help_rate_uuid (uuid),
    UNIQUE KEY uk_help_rate_subject_window (subject_hash, window_start),
    KEY idx_help_rate_expiry (window_start)
);
