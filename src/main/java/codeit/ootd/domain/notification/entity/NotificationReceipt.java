package codeit.ootd.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 알림을 읽어서 삭제한 뒤에도 같은 이벤트로 알림이 다시 만들어지지 않게 보관한다. */
@Entity
@Table(name = "notification_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationReceipt {
    @Id
    private UUID id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Version
    private Long version;

    public NotificationReceipt(UUID eventId) {
        this.id = eventId;
        this.processedAt = Instant.now();
    }
}
