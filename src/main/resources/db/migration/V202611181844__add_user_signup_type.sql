ALTER TABLE users
    ADD COLUMN signup_type VARCHAR(20)
        NOT NULL DEFAULT 'LOCAL';

ALTER TABLE users
    ADD CONSTRAINT chk_users_signup_type
        CHECK (signup_type IN ('LOCAL', 'SOCIAL'));
