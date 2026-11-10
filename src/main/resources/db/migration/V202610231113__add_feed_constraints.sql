-- 피드 관련 시간 컬럼 타입 변경
ALTER TABLE feeds
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE feed_likes
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

ALTER TABLE feed_comments
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;


-- 동일 사용자의 동일 피드 중복 좋아요 방지
ALTER TABLE feed_likes
    ADD CONSTRAINT uk_feed_likes_feed_user
        UNIQUE (feed_id, user_id);


-- 피드의 작성자 및 날씨 참조
ALTER TABLE feeds
    ADD CONSTRAINT fk_feeds_author
        FOREIGN KEY (author_id) REFERENCES users(id);

ALTER TABLE feeds
    ADD CONSTRAINT fk_feeds_weather
        FOREIGN KEY (weather_id) REFERENCES weather(id);


-- 피드에 포함된 의상 참조
ALTER TABLE feed_clothes
    ADD CONSTRAINT fk_feed_clothes_feed
        FOREIGN KEY (feed_id) REFERENCES feeds(id);

ALTER TABLE feed_clothes
    ADD CONSTRAINT fk_feed_clothes_clothes
        FOREIGN KEY (clothes_id) REFERENCES clothes(id);


-- 좋아요의 피드 및 사용자 참조
ALTER TABLE feed_likes
    ADD CONSTRAINT fk_feed_likes_feed
        FOREIGN KEY (feed_id) REFERENCES feeds(id);

ALTER TABLE feed_likes
    ADD CONSTRAINT fk_feed_likes_user
        FOREIGN KEY (user_id) REFERENCES users(id);


-- 댓글의 피드 및 작성자 참조
ALTER TABLE feed_comments
    ADD CONSTRAINT fk_feed_comments_feed
        FOREIGN KEY (feed_id) REFERENCES feeds(id);

ALTER TABLE feed_comments
    ADD CONSTRAINT fk_feed_comments_author
        FOREIGN KEY (author_id) REFERENCES users(id);