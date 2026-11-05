package codeit.ootd.domain.notification.service;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDto saveNotification(UUID receiverId, String title, String content,
            NotificationLevel level) {
        Notification saved = notificationRepository.save(
                new Notification(receiverId, title, content, level));
        return new NotificationDto(
                saved.getId(), saved.getCreatedAt(), saved.getReceiverId(),
                saved.getTitle(), saved.getContent(), saved.getLevel());
    }
}
