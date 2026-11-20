package codeit.ootd.domain.notification.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    public NotificationOutbox(String payload, String messageKey) {
        this.payload = payload;
        this.messageKey = messageKey;
    }
}
