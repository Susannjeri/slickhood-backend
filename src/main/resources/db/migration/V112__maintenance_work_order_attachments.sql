CREATE TABLE pms_maintenance_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active BIT(1) NOT NULL DEFAULT b'1',
    created_by BIGINT NULL,
    last_modified_date DATETIME(6) NULL,
    work_order_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    file_ref VARCHAR(800) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_attachment_uuid (uuid),
    KEY idx_maintenance_attachment_order (work_order_id, active, created_on),
    CONSTRAINT fk_maintenance_attachment_order FOREIGN KEY (work_order_id)
        REFERENCES pms_maintenance_work_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
