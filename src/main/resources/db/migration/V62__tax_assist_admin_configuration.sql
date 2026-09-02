CREATE TABLE pms_tax_assist_configuration (
    id BIGINT NOT NULL,
    estimates_enabled BIT NOT NULL DEFAULT 1,
    connection_requests_enabled BIT NOT NULL DEFAULT 0,
    legal_notice_version VARCHAR(40) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_tax_assist_singleton CHECK (id = 1)
);

INSERT INTO pms_tax_assist_configuration
    (id, estimates_enabled, connection_requests_enabled, legal_notice_version)
VALUES (1, 1, 0, 'tax-guidance-2026-09');
