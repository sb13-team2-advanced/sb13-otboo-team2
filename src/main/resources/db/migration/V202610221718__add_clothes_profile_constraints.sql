ALTER TABLE profiles
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE clothes
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

ALTER TABLE clothes_attribute_definition
    ALTER COLUMN created_at TYPE TIMESTAMPTZ;

ALTER TABLE profiles
    ADD CONSTRAINT uk_profiles_user UNIQUE (user_id);

ALTER TABLE profiles
    ADD CONSTRAINT fk_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE clothes
    ADD CONSTRAINT fk_clothes_user
        FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE clothes_attribute
    ADD CONSTRAINT fk_clothes_attribute_clothes
        FOREIGN KEY (clothes_id) REFERENCES clothes(id);

ALTER TABLE clothes_attribute
    ADD CONSTRAINT fk_clothes_attribute_definition
        FOREIGN KEY (definition_id) REFERENCES clothes_attribute_definition(id);

ALTER TABLE clothes_attribute
    ADD CONSTRAINT uk_clothes_attribute_clothes_definition
        UNIQUE (clothes_id, definition_id);

ALTER TABLE clothes_attribute_definition
    ADD CONSTRAINT uk_clothes_attribute_definition_name UNIQUE (name);
