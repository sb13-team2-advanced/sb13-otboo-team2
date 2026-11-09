package codeit.ootd.domain.notification.service;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.dto.response.NotificationDtoCursorResponse;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.repository.NotificationQueryRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_LIMIT = 30;

    private final NotificationRepository notificationRepository;
    private final NotificationQueryRepository notificationQueryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDto saveNotification(UUID receiverId, String title, String content,
            NotificationLevel level) {
        Notification saved = notificationRepository.save(
                new Notification(receiverId, title, content, level));
        return new NotificationDto(
                saved.getId(), saved.getCreatedAt(), saved.getReceiverId(),
                saved.getTitle(), saved.getContent(), saved.getLevel());
    }

    @Transactional(readOnly = true)
    public NotificationDtoCursorResponse getNotifications(UUID userId, String cursor, UUID idAfter, int limit) {
        if (userId == null) throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        if (limit < 1 || limit > MAX_LIMIT) throw new OtbooException(ErrorCode.INVALID_REQUEST);

        Instant after = null;
        try {
            if (cursor != null) after = Instant.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }
        if (after != null && idAfter == null) throw new OtbooException(ErrorCode.INVALID_REQUEST);
        // 두 커서가 있으면 기준 알림이 삭제되어도 다음 페이지를 조회할 수 있다.
        if (after == null && idAfter != null) {
            Notification anchor = notificationRepository.findByIdAndReceiverId(idAfter, userId)
                    .orElseThrow(() -> new OtbooException(ErrorCode.INVALID_REQUEST));
            after = anchor.getCreatedAt();
        }

        List<Notification> rows = notificationQueryRepository.findList(userId, after, idAfter, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<NotificationDto> data = rows.stream().limit(limit)
                .map(notification -> new NotificationDto(
                        notification.getId(), notification.getCreatedAt(), notification.getReceiverId(),
                        notification.getTitle(), notification.getContent(), notification.getLevel()))
                .toList();
        Notification last = hasNext ? rows.get(limit - 1) : null;
        return new NotificationDtoCursorResponse(
                data, last == null ? null : last.getCreatedAt().toString(),
                last == null ? null : last.getId(), hasNext,
                notificationQueryRepository.countByReceiverId(userId),
                "createdAt", SortDirection.DESCENDING);
    }

    @Transactional
    public void readNotification(UUID notificationId, UUID userId) {
        if (userId == null) throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
                .orElseThrow(() -> new OtbooException(ErrorCode.INVALID_REQUEST));
        notificationRepository.delete(notification);
    }
}
