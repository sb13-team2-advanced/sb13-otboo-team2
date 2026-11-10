package codeit.ootd.domain.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feed_clothes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedClothes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "feed_id", nullable = false)
    private UUID feedId;

    @Column(name = "clothes_id", nullable = false)
    private UUID clothesId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public FeedClothes(UUID feedId, UUID clothesId, int displayOrder) {
        this.feedId = feedId;
        this.clothesId = clothesId;
        this.displayOrder = displayOrder;
    }
}