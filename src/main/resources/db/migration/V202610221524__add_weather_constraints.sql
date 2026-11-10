ALTER TABLE weather_location
    ADD COLUMN updated_at TIMESTAMPTZ;

ALTER TABLE weather_forecast
    ADD COLUMN updated_at TIMESTAMPTZ;

ALTER TABLE weather
    ADD COLUMN updated_at TIMESTAMPTZ;


UPDATE weather_location
SET updated_at = created_at
WHERE updated_at IS NULL;

UPDATE weather_forecast
SET updated_at = created_at
WHERE updated_at IS NULL;

UPDATE weather
SET updated_at = created_at
WHERE updated_at IS NULL;


ALTER TABLE weather_location
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE weather_forecast
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE weather
    ALTER COLUMN updated_at SET NOT NULL;


ALTER TABLE weather_location
    ADD CONSTRAINT uk_weather_location_cell
        UNIQUE (cell_id);

ALTER TABLE weather_forecast
    ADD CONSTRAINT uk_weather_forecast_location_time
        UNIQUE (location_id, forecast_at);

ALTER TABLE weather
    ADD CONSTRAINT uk_weather_location_date
        UNIQUE (location_id, weather_date);

ALTER TABLE weather_forecast
    ADD CONSTRAINT fk_weather_forecast_location
        FOREIGN KEY (location_id)
            REFERENCES weather_location(id);

ALTER TABLE weather
    ADD CONSTRAINT fk_weather_location
        FOREIGN KEY (location_id)
            REFERENCES weather_location(id);


ALTER TABLE weather_location
    ALTER COLUMN resolution SET DEFAULT 8;
