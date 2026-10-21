CREATE TABLE google_play_notifications (
    id VARCHAR(128) PRIMARY KEY,
    version BIGINT,
    payload_hash VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64),
    encrypted_token TEXT,
    type VARCHAR(48) NOT NULL,
    event_time_millis BIGINT NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    received_at TIMESTAMP NOT NULL,
    next_retry_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);
CREATE INDEX idx_google_play_notification_due ON google_play_notifications(processing_status, next_retry_at);
CREATE INDEX idx_google_play_notification_retention ON google_play_notifications(processed_at);
