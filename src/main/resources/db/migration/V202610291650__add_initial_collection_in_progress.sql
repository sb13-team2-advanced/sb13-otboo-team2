ALTER TABLE weather_location
    ADD COLUMN initial_collection_in_progress BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE weather_location
    ADD COLUMN initial_collection_token UUID;