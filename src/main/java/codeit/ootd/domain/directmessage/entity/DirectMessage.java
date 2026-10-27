package codeit.ootd.domain.directmessage.entity;

import codeit.ootd.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "direct_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseEntity {

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    public DirectMessage(UUID senderId, UUID receiverId, String content) {
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }
}
