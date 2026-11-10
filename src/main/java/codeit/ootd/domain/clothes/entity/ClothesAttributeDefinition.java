package codeit.ootd.domain.clothes.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Getter
@Entity
@Table(name = "clothes_attribute_definition", uniqueConstraints = {
    @UniqueConstraint(name = "uk_clothes_attribute_definition_name", columnNames = "name")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 선택 가능한 값 목록. selectable_attribute 테이블의 sort 컬럼이 순서를 나타낸다.
     * value는 H2 등에서 예약어라 따옴표로 감싼다.
     */
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(
        name = "selectable_attribute",
        joinColumns = @JoinColumn(name = "definition_id")
    )
    @OrderColumn(name = "sort")
    @Column(name = "\"value\"", nullable = false, length = 50)
    private List<String> selectableValues = new ArrayList<>();

    public ClothesAttributeDefinition(String name, List<String> selectableValues) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.selectableValues = new ArrayList<>(
            Objects.requireNonNull(selectableValues, "selectableValues must not be null"));
    }

    public void update(String name, List<String> selectableValues) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.selectableValues.clear();
        this.selectableValues.addAll(
            Objects.requireNonNull(selectableValues, "selectableValues must not be null"));
    }
}
