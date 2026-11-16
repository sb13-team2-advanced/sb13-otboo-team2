CREATE INDEX idx_direct_messages_conversation_cursor
    ON direct_messages (sender_id, receiver_id, created_at DESC, id DESC);
