CREATE TABLE direct_messages
(
    id          UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    sender_id   UUID        NOT NULL,
    receiver_id UUID        NOT NULL,
    content     TEXT        NOT NULL,

    CONSTRAINT pk_direct_messages PRIMARY KEY (id)
);
