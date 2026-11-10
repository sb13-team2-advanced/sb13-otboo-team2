CREATE TABLE "selectable_attribute"
(
    "sort"          INT         NOT NULL,
    "definition_id" UUID        NOT NULL,
    "value"         VARCHAR(50) NOT NULL
);

CREATE TABLE "weather_location"
(
    "id"                UUID             NOT NULL,
    "cell_id"           VARCHAR(32)      NOT NULL,
    "resolution"        SMALLINT         NOT NULL,
    "latitude"          DOUBLE PRECISION NOT NULL,
    "longitude"         DOUBLE PRECISION NOT NULL,
    "last_collected_at" TIMESTAMPTZ NULL,
    "next_refresh_at"   TIMESTAMPTZ      NOT NULL,
    "created_at"        TIMESTAMPTZ      NOT NULL
);

CREATE TABLE "oauth_accounts"
(
    "id"               UUID         NOT NULL,
    "user_id"          UUID         NOT NULL,
    "provider"         VARCHAR(20)  NOT NULL,
    "provider_user_id" VARCHAR(255) NOT NULL,
    "created_at"       TIMESTAMP    NOT NULL
);

COMMENT ON COLUMN "oauth_accounts"."provider" IS 'UNIQUE';

COMMENT ON COLUMN "oauth_accounts"."provider_user_id" IS 'UNIQUE';

CREATE TABLE "users"
(
    "id"         UUID         NOT NULL,
    "email"      VARCHAR(100) NOT NULL,
    "password"   VARCHAR(255) NOT NULL,
    "name"       VARCHAR(100) NOT NULL,
    "role"       VARCHAR(20)  NOT NULL,
    "locked"     BOOLEAN      NOT NULL,
    "status"     VARCHAR(20)  NOT NULL,
    "created_at" TIMESTAMP    NOT NULL,
    "updated_at" TIMESTAMP    NOT NULL,
    "deleted_at" TIMESTAMP NULL
);

COMMENT ON COLUMN "users"."email" IS 'UNIQUE (uk_users_email)';

COMMENT ON COLUMN "users"."locked" IS '기본값 FALSE';

CREATE TABLE "direct_message"
(
    "id"          UUID        NOT NULL,
    "content"     VARCHAR     NOT NULL,
    "created_at"  TIMESTAMPTZ NOT NULL,
    "sender_id"   UUID        NOT NULL,
    "receiver_id" UUID        NOT NULL
);

CREATE TABLE "feed_likes"
(
    "id"         UUID      NOT NULL,
    "feed_id"    UUID      NOT NULL,
    "user_id"    UUID      NOT NULL,
    "created_at" TIMESTAMP NOT NULL
);

CREATE TABLE "profiles"
(
    "id"                      UUID        NOT NULL,
    "name"                    VARCHAR(50) NOT NULL,
    "gender"                  VARCHAR(10) NULL,
    "birth_date"              DATE NULL,
    "latitude"                DOUBLE PRECISION NULL,
    "longitude"               DOUBLE PRECISION NULL,
    "grid_x"                  INTEGER NULL,
    "grid_y"                  INTEGER NULL,
    "location_names"          TEXT NULL,
    "temperature_sensitivity" INTEGER NULL,
    "profile_image_url"       VARCHAR(500) NULL,
    "created_at"              TIMESTAMP   NOT NULL,
    "updated_at"              TIMESTAMP   NOT NULL,
    "user_id"                 UUID        NOT NULL
);

CREATE TABLE "follow"
(
    "follow_id"  UUID NOT NULL,
    "user_id" UUID NOT NULL
);

CREATE TABLE "weather_forecast"
(
    "id"                        UUID             NOT NULL,
    "location_id"               UUID             NOT NULL,
    "forecast_at"               TIMESTAMPTZ      NOT NULL,
    "collected_at"              TIMESTAMPTZ      NOT NULL,
    "temperature"               DOUBLE PRECISION NOT NULL,
    "humidity"                  SMALLINT         NOT NULL,
    "weather_code"              INTEGER          NOT NULL,
    "precipitation_probability" DOUBLE PRECISION NOT NULL,
    "rain_amount"               DOUBLE PRECISION NOT NULL,
    "snow_amount"               DOUBLE PRECISION NOT NULL,
    "wind_speed"                DOUBLE PRECISION NOT NULL,
    "created_at"                TIMESTAMPTZ      NOT NULL
);

CREATE TABLE "auth_sessions"
(
    "id"         UUID      NOT NULL,
    "user_id"    UUID      NOT NULL,
    "created_at" TIMESTAMP NOT NULL,
    "expires_at" TIMESTAMP NOT NULL,
    "revoked_at" TIMESTAMP NULL
);

COMMENT ON COLUMN "auth_sessions"."user_id" IS 'UNIQUE';

CREATE TABLE "refresh_tokens"
(
    "id"         UUID         NOT NULL,
    "session_id" UUID         NOT NULL,
    "token_hash" VARCHAR(255) NOT NULL,
    "created_at" TIMESTAMP    NOT NULL,
    "expires_at" TIMESTAMP    NOT NULL
);

COMMENT ON COLUMN "refresh_tokens"."session_id" IS 'UNIQUE';

CREATE TABLE "weather"
(
    "id"                        UUID             NOT NULL,
    "location_id"               UUID             NOT NULL,
    "weather_date"              DATE             NOT NULL,
    "calculated_at"             TIMESTAMPTZ      NOT NULL,
    "temperature_avg"           DOUBLE PRECISION NOT NULL,
    "temperature_min"           DOUBLE PRECISION NOT NULL,
    "temperature_max"           DOUBLE PRECISION NOT NULL,
    "humidity_avg"              SMALLINT         NOT NULL,
    "weather_code"              INTEGER          NOT NULL,
    "precipitation_probability" DOUBLE PRECISION NOT NULL,
    "rain_amount"               DOUBLE PRECISION NOT NULL,
    "snow_amount"               DOUBLE PRECISION NOT NULL,
    "wind_speed"                DOUBLE PRECISION NOT NULL,
    "forecast_slot_count"       SMALLINT         NOT NULL,
    "created_at"                TIMESTAMPTZ      NOT NULL
);

CREATE TABLE "feed_clothes"
(
    "id"            UUID    NOT NULL,
    "feed_id"       UUID    NOT NULL,
    "display_order" INTEGER NOT NULL,
    "clothes_id"    UUID    NOT NULL
);

CREATE TABLE "clothes_attribute_definition"
(
    "id"         UUID        NOT NULL,
    "name"       VARCHAR(50) NOT NULL,
    "created_at" TIMESTAMP   NOT NULL
);

CREATE TABLE "feeds"
(
    "id"                                         UUID              NOT NULL,
    "author_id"                                  UUID              NOT NULL,
    "weather_id"                                 UUID              NOT NULL,
    "content"                                    TEXT              NOT NULL,
    "like_count"                                 BIGINT  DEFAULT 0 NOT NULL,
    "comment_count"                              INTEGER DEFAULT 0 NOT NULL,
    "created_at"                                 TIMESTAMP         NOT NULL,
    "updated_at"                                 TIMESTAMP         NOT NULL,
    "weather_sky_status"                         VARCHAR(20)       NOT NULL,
    "weather_precipitation_type"                 VARCHAR(20)       NOT NULL,
    "weather_precipitation_amount"               DOUBLE PRECISION  NOT NULL,
    "weather_precipitation_probability"          DOUBLE PRECISION  NOT NULL,
    "weather_temperature_current"                DOUBLE PRECISION  NOT NULL,
    "weather_temperature_compared_to_day_before" DOUBLE PRECISION  NOT NULL,
    "weather_temperature_min"                    DOUBLE PRECISION  NOT NULL,
    "weather_temperature_max"                    DOUBLE PRECISION  NOT NULL
);

CREATE TABLE "feed_comments"
(
    "id"         UUID      NOT NULL,
    "feed_id"    UUID      NOT NULL,
    "author_id"  UUID      NOT NULL,
    "content"    TEXT      NOT NULL,
    "created_at" TIMESTAMP NOT NULL
);

CREATE TABLE "clothes"
(
    "id"         UUID        NOT NULL,
    "user_id"    UUID        NOT NULL,
    "name"       VARCHAR(50) NOT NULL,
    "image_url"  TEXT        NOT NULL,
    "type"       VARCHAR(30) NOT NULL,
    "created_at" TIMESTAMP   NOT NULL,
    "updated_at" TIMESTAMP   NOT NULL
);

CREATE TABLE "temporary_passwords"
(
    "id"            UUID         NOT NULL,
    "user_id"       UUID         NOT NULL,
    "password_hash" VARCHAR(255) NOT NULL,
    "expires_at"    TIMESTAMP    NOT NULL,
    "created_at"    TIMESTAMP    NOT NULL
);

COMMENT ON COLUMN "temporary_passwords"."user_id" IS 'UNIQUE';

CREATE TABLE "notification"
(
    "id"          UUID        NOT NULL,
    "receiver_id" UUID        NOT NULL,
    "title"       TEXT        NOT NULL,
    "content"     VARCHAR     NOT NULL,
    "created_at"  TIMESTAMPTZ NOT NULL,
    "level"       VARCHAR(10) NOT NULL
);

CREATE TABLE "clothes_attribute"
(
    "id"            UUID        NOT NULL,
    "clothes_id"    UUID        NOT NULL,
    "definition_id" UUID        NOT NULL,
    "value"         VARCHAR(50) NOT NULL
);

ALTER TABLE "selectable_attribute"
    ADD CONSTRAINT "PK_SELECTABLEATTRIBUTE" PRIMARY KEY ("sort", "definition_id");

ALTER TABLE "weather_location"
    ADD CONSTRAINT "PK_WEATHER_LOCATION" PRIMARY KEY ("id");

ALTER TABLE "oauth_accounts"
    ADD CONSTRAINT "PK_OAUTHACCOUNTS" PRIMARY KEY ("id");

ALTER TABLE "users"
    ADD CONSTRAINT "PK_USERS" PRIMARY KEY ("id");

ALTER TABLE "direct_message"
    ADD CONSTRAINT "PK_DIRECT_MESSAGE" PRIMARY KEY ("id");

ALTER TABLE "feed_likes"
    ADD CONSTRAINT "PK_FEED_LIKES" PRIMARY KEY ("id");

ALTER TABLE "profiles"
    ADD CONSTRAINT "PK_PROFILES" PRIMARY KEY ("id");

ALTER TABLE "follow"
    ADD CONSTRAINT "PK_FOLLOW" PRIMARY KEY ("follow_id", "user_id");

ALTER TABLE "weather_forecast"
    ADD CONSTRAINT "PK_WEATHER_FORECAST" PRIMARY KEY ("id");

ALTER TABLE "auth_sessions"
    ADD CONSTRAINT "PK_AUTHSESSIONS" PRIMARY KEY ("id");

ALTER TABLE "refresh_tokens"
    ADD CONSTRAINT "PK_REFRESHTOKENS" PRIMARY KEY ("id");

ALTER TABLE "weather"
    ADD CONSTRAINT "PK_WEATHER" PRIMARY KEY ("id");

ALTER TABLE "feed_clothes"
    ADD CONSTRAINT "PK_FEED_CLOTHES" PRIMARY KEY ("id");

ALTER TABLE "clothes_attribute_definition"
    ADD CONSTRAINT "PK_CLOTHESATTRIBUTEDEFINITION" PRIMARY KEY ("id");

ALTER TABLE "feeds"
    ADD CONSTRAINT "PK_FEEDS" PRIMARY KEY ("id");

ALTER TABLE "feed_comments"
    ADD CONSTRAINT "PK_FEED_COMMENTS" PRIMARY KEY ("id");

ALTER TABLE "clothes"
    ADD CONSTRAINT "PK_CLOTHES" PRIMARY KEY ("id");

ALTER TABLE "temporary_passwords"
    ADD CONSTRAINT "PK_TEMPORARYPASSWORDS" PRIMARY KEY ("id");

ALTER TABLE "notification"
    ADD CONSTRAINT "PK_NOTIFICATION" PRIMARY KEY ("id");

ALTER TABLE "clothes_attribute"
    ADD CONSTRAINT "PK_CLOTHESATTRIBUTE" PRIMARY KEY ("id");

ALTER TABLE "selectable_attribute"
    ADD CONSTRAINT "FK_ClothesAttributeDefinition_TO_SelectableAttribute_1" FOREIGN KEY ("definition_id")
        REFERENCES "clothes_attribute_definition" ("id");

