package codeit.ootd.domain.notification.entity;

import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationLevel level;

    public Notification(UUID receiverId, String title, String content, NotificationLevel level) {
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.level = Objects.requireNonNull(level, "level must not be null");
    }
}
