CREATE TABLE certificates (
    id VARCHAR(255) PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    subscription_id VARCHAR(255) NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    participant_email VARCHAR(255) NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    issued_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    file_key VARCHAR(255),
    CONSTRAINT uk_certificates_subscription_event UNIQUE (subscription_id, event_id)
);

CREATE TABLE outbox_message (
    id VARCHAR(255) PRIMARY KEY,
    payload VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_pending ON outbox_message (processed_at, created_at);
