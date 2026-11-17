ALTER TABLE users
    ADD COLUMN temporary_password VARCHAR(255),
    ADD COLUMN temporary_password_expires_at TIMESTAMP WITH TIME ZONE;
