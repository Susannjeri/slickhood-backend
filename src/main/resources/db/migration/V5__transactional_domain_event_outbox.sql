CREATE TABLE pms_domain_event_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 event_id VARCHAR(36) NOT NULL, dedupe_key VARCHAR(180) NOT NULL, event_type VARCHAR(80) NOT NULL,
 aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(120) NOT NULL, payload LONGTEXT NOT NULL,
 status VARCHAR(20) NOT NULL, attempts INT NOT NULL, next_attempt_at DATETIME(6) NOT NULL,
 processing_started_at DATETIME(6), processed_at DATETIME(6), correlation_id VARCHAR(36), last_error VARCHAR(1000),
 PRIMARY KEY(id), UNIQUE KEY uk_outbox_uuid(uuid), UNIQUE KEY uk_outbox_event_id(event_id),
 UNIQUE KEY uk_outbox_dedupe_key(dedupe_key), KEY idx_outbox_dispatch(status,next_attempt_at),
 KEY idx_outbox_aggregate(aggregate_type,aggregate_id)
);
