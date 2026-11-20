CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL
);

CREATE INDEX idx_notification_outbox_created_at ON notification_outbox (created_at, id);

CREATE TABLE notification_receipts (
    id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);
