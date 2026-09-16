CREATE TABLE pms_notification_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    active BIT(1) NOT NULL DEFAULT b'1',
    created_by BIGINT,
    last_modified_date DATETIME(6),
    user_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    email_enabled BIT(1) NOT NULL DEFAULT b'0',
    sms_enabled BIT(1) NOT NULL DEFAULT b'0',
    whatsapp_enabled BIT(1) NOT NULL DEFAULT b'0',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preference_uuid UNIQUE (uuid),
    CONSTRAINT uk_notification_preference_user_category UNIQUE (user_id, category),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES pms_users(id)
);

CREATE TABLE pms_notification_channel_consent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    active BIT(1) NOT NULL DEFAULT b'1',
    created_by BIGINT,
    last_modified_date DATETIME(6),
    user_id BIGINT NOT NULL,
    channel VARCHAR(24) NOT NULL,
    consented BIT(1) NOT NULL DEFAULT b'0',
    consent_version VARCHAR(80),
    consented_at DATETIME(6),
    revoked_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_consent_uuid UNIQUE (uuid),
    CONSTRAINT uk_notification_consent_user_channel UNIQUE (user_id, channel),
    CONSTRAINT fk_notification_consent_user FOREIGN KEY (user_id) REFERENCES pms_users(id)
);

CREATE INDEX idx_notification_preference_user ON pms_notification_preference(user_id, active);
CREATE INDEX idx_notification_consent_user ON pms_notification_channel_consent(user_id, active);
