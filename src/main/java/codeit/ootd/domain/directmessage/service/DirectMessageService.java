package codeit.ootd.domain.directmessage.service;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.directmessage.dto.request.DirectMessageSendRequest;
import codeit.ootd.domain.directmessage.entity.DirectMessage;
import codeit.ootd.domain.directmessage.event.DirectMessageSentEvent;
import codeit.ootd.domain.directmessage.repository.DirectMessageRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.dto.response.UserSummary;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserStatus;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;
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
        return dto;
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new OtbooException(ErrorCode.USER_NOT_FOUND));
    }

    private UserSummary summary(User user) {
        String imageUrl = profileRepository.findByUserId(user.getId())
                .map(Profile::getProfileImageUrl).orElse(null);
        return new UserSummary(user.getId(), user.getName(), imageUrl);
    }
}
