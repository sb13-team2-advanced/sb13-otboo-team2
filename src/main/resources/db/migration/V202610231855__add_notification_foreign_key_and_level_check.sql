ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(id);

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_level
        CHECK (level IN ('INFO', 'WARNING', 'ERROR'));
