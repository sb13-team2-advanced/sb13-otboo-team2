-- 초기 스키마(FK_ClothesAttributeDefinition_TO_SelectableAttribute_1)에 이미 같은 FK가 있어 중복 제거
ALTER TABLE selectable_attribute DROP CONSTRAINT IF EXISTS fk_selectable_attribute_definition;
