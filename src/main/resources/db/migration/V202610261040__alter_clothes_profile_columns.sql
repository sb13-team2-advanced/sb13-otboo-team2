-- 의상 이미지는 선택 사항 (명세에서 multipart의 image는 필수가 아님)
ALTER TABLE clothes
    ALTER COLUMN image_url DROP NOT NULL;

-- 이름은 users.name 하나만 기준으로 사용
ALTER TABLE profiles
    DROP COLUMN name;

-- 속성 선택 값은 속성 정의에 속하므로 외래 키 추가
ALTER TABLE selectable_attribute
    ADD CONSTRAINT fk_selectable_attribute_definition
        FOREIGN KEY (definition_id) REFERENCES clothes_attribute_definition(id);
