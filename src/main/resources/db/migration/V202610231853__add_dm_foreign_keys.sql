ALTER TABLE direct_messages
    ADD CONSTRAINT fk_direct_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users(id);

ALTER TABLE direct_messages
    ADD CONSTRAINT fk_direct_messages_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(id);
