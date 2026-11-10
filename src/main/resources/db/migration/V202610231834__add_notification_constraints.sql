CREATE TABLE notifications
(
    id          UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    receiver_id UUID         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    level       VARCHAR(255) NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id)
);
