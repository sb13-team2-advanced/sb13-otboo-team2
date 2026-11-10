CREATE TABLE follows
(
    id          UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    follower_id UUID        NOT NULL,
    followee_id UUID        NOT NULL,

    CONSTRAINT pk_follows PRIMARY KEY (id),
    CONSTRAINT uk_follows_follower_followee UNIQUE (follower_id, followee_id)
);
