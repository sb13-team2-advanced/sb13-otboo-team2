package codeit.ootd.domain.directmessage.service;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.directmessage.dto.DirectMessageDtoCursorResponse;
import codeit.ootd.domain.directmessage.dto.request.DirectMessageSendRequest;
import codeit.ootd.domain.directmessage.entity.DirectMessage;
import codeit.ootd.domain.directmessage.event.DirectMessageSentEvent;
import codeit.ootd.domain.directmessage.exception.DirectMessageInvalidCursorException;
import codeit.ootd.domain.directmessage.exception.DirectMessageUserNotFoundException;
import codeit.ootd.domain.directmessage.repository.DirectMessageQueryRepository;
import codeit.ootd.domain.directmessage.repository.DirectMessageRepository;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.dto.response.UserSummary;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private static final int MAX_LIMIT = 30;

    private final DirectMessageRepository directMessageRepository;
    private final DirectMessageQueryRepository directMessageQueryRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DirectMessageDto send(DirectMessageSendRequest request, UUID currentUserId) {
        if (currentUserId == null) throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        if (request == null || request.senderId() == null || request.receiverId() == null
                || request.content() == null || request.content().isBlank()) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }
        if (!currentUserId.equals(request.senderId())) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }
        User sender = user(currentUserId);
        if (sender.isLocked()) throw new OtbooException(ErrorCode.ACCOUNT_LOCKED);
        if (sender.getStatus() != UserStatus.ACTIVE) {
            throw new OtbooException(ErrorCode.ACCOUNT_INACTIVE);
        }
        User receiver = user(request.receiverId());
        if (receiver.getStatus() != UserStatus.ACTIVE) {
            throw new OtbooException(ErrorCode.ACCOUNT_INACTIVE);
        }

        DirectMessage saved = directMessageRepository.save(
                new DirectMessage(sender.getId(), receiver.getId(), request.content()));
        DirectMessageDto dto = new DirectMessageDto(saved.getId(), saved.getCreatedAt(),
                summary(sender), summary(receiver), saved.getContent());
        // DB 커밋에 성공한 메시지만 구독자에게 전달한다.
        eventPublisher.publishEvent(new DirectMessageSentEvent(dto));
        eventPublisher.publishEvent(new NotificationRequestedEvent(
                receiver.getId(), "새 DM이 도착했습니다.",
                sender.getName() + "님이 DM을 보냈습니다.", NotificationLevel.INFO));
        return dto;
    }

    @Transactional(readOnly = true)
    public DirectMessageDtoCursorResponse getMessages(UUID currentUserId, UUID userId,
            String cursor, UUID idAfter, int limit) {
        if (currentUserId == null) throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        if (userId == null || limit < 1 || limit > MAX_LIMIT) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }
        User currentUser = user(currentUserId);
        User otherUser = currentUserId.equals(userId) ? currentUser : user(userId);
        Instant after = resolveCursor(currentUserId, userId, cursor, idAfter);
        List<DirectMessage> rows = directMessageQueryRepository.findList(
                currentUserId, userId, after, idAfter, limit + 1);
        boolean hasNext = rows.size() > limit;

        // Only two participants are mapped, regardless of the number of messages.
        UserSummary mine = rows.isEmpty() ? null : summary(currentUser);
        UserSummary other = rows.isEmpty() ? null
                : currentUserId.equals(userId) ? mine : summary(otherUser);
        List<DirectMessageDto> data = rows.stream().limit(limit)
                .map(message -> new DirectMessageDto(message.getId(), message.getCreatedAt(),
                        message.getSenderId().equals(currentUserId) ? mine : other,
                        message.getReceiverId().equals(currentUserId) ? mine : other,
                        message.getContent()))
                .toList();
        DirectMessage last = hasNext ? rows.get(limit - 1) : null;
        return new DirectMessageDtoCursorResponse(data,
                last == null ? null : last.getCreatedAt().toString(),
                last == null ? null : last.getId(), hasNext,
                directMessageQueryRepository.countConversation(currentUserId, userId),
                "createdAt", SortDirection.DESCENDING);
    }

    private Instant resolveCursor(UUID currentUserId, UUID userId, String cursor, UUID idAfter) {
        Instant after = null;
        try {
            if (cursor != null) after = Instant.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new DirectMessageInvalidCursorException();
        }
        if (after != null && idAfter == null) throw new DirectMessageInvalidCursorException();
        if (idAfter == null) return null;
        DirectMessage anchor = directMessageRepository.findById(idAfter)
                .orElseThrow(DirectMessageInvalidCursorException::new);
        boolean belongsToConversation =
                anchor.getSenderId().equals(currentUserId) && anchor.getReceiverId().equals(userId)
                || anchor.getSenderId().equals(userId) && anchor.getReceiverId().equals(currentUserId);
        if (!belongsToConversation || after != null && !after.equals(anchor.getCreatedAt())) {
            throw new DirectMessageInvalidCursorException();
        }
        return anchor.getCreatedAt();
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(DirectMessageUserNotFoundException::new);
    }

    private UserSummary summary(User user) {
        String imageUrl = profileRepository.findByUserId(user.getId())
                .map(Profile::getProfileImageUrl).orElse(null);
        return new UserSummary(user.getId(), user.getName(), imageUrl);
    }
}
