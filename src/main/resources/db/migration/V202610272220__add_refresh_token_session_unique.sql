ALTER TABLE refresh_tokens
    ADD CONSTRAINT uk_refresh_tokens_session_id
        UNIQUE (session_id);