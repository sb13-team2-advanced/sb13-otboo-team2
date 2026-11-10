package codeit.ootd.domain.clothes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 의상이 가진 속성 값. 시간 컬럼이 없는 테이블이라 BaseEntity를 상속하지 않고 자체 id를 사용한다.
 */
@Getter
@Entity
@Table(name = "clothes_attribute", uniqueConstraints = {
    @UniqueConstraint(name = "uk_clothes_attribute_clothes_definition", columnNames = {
        "clothes_id", "definition_id"
    })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "clothes_id", nullable = false, updatable = false)
    private UUID clothesId;

    @Column(name = "definition_id", nullable = false, updatable = false)
    private UUID definitionId;

    @Column(name = "\"value\"", nullable = false, length = 50)
    private String value;

    public ClothesAttribute(UUID clothesId, UUID definitionId, String value) {
        this.clothesId = Objects.requireNonNull(clothesId, "clothesId must not be null");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public void changeValue(String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }
}
