ALTER TABLE oauth_accounts
    ADD CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_id)
            REFERENCES users(id);

ALTER TABLE oauth_accounts
    ADD CONSTRAINT uk_oauth_accounts_provider_user
        UNIQUE (provider, provider_user_id);

ALTER TABLE oauth_accounts
    ADD CONSTRAINT uk_oauth_accounts_user_provider
        UNIQUE (user_id, provider);