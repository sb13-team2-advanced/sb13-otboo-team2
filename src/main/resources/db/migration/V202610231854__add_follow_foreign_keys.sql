ALTER TABLE follows
    ADD CONSTRAINT fk_follows_follower
        FOREIGN KEY (follower_id) REFERENCES users(id);

ALTER TABLE follows
    ADD CONSTRAINT fk_follows_followee
        FOREIGN KEY (followee_id) REFERENCES users(id);
