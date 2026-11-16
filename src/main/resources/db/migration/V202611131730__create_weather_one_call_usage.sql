CREATE TABLE weather_one_call_usage (
    usage_date DATE PRIMARY KEY,
    request_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_weather_one_call_usage_request_count
        CHECK (request_count >= 0)
);