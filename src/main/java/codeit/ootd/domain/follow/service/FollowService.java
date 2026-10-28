package codeit.ootd.domain.follow.service;

import codeit.ootd.domain.follow.dto.response.*;
import codeit.ootd.domain.follow.dto.request.FollowCreateRequest;
import codeit.ootd.domain.follow.entity.Follow;
import codeit.ootd.domain.follow.exception.FollowAlreadyExistsException;
import codeit.ootd.domain.follow.repository.FollowRepository;
import codeit.ootd.domain.follow.repository.FollowQueryRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.dto.response.UserSummary;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.OtbooException;
import static codeit.ootd.global.exception.ErrorCode.*;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {
    private final FollowRepository followRepository;
    private final FollowQueryRepository followQueryRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 인증 구현과 연결할 때 Principal의 이름을 이메일로 제공
    public UUID currentUserId(String username) {
        if (username == null) throw new OtbooException(AUTHENTICATION_REQUIRED);
        return userRepository.findByEmail(username).map(User::getId)
            .orElseThrow(() -> new OtbooException(AUTHENTICATION_REQUIRED));
    }

    @Transactional
    public FollowDto create(FollowCreateRequest request, UUID currentUserId) {
        if (!currentUserId.equals(request.followerId())) throw new OtbooException(ACCESS_DENIED);
        User follower = user(request.followerId());
        User followee = user(request.followeeId());
        if (followRepository.existsByFollowerIdAndFolloweeId(follower.getId(), followee.getId())) {
            throw new FollowAlreadyExistsException();
        }
        Follow follow;
        try {
            follow = followRepository.saveAndFlush(new Follow(follower.getId(), followee.getId()));
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 후 동시에 생성된 중복 요청도 DB 유일 제약으로 차단
            if (isDuplicateFollow(exception)) throw new FollowAlreadyExistsException();
            throw exception;
        }
        return new FollowDto(follow.getId(), summary(followee), summary(follower));
    }

    private boolean isDuplicateFollow(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                && "uk_follows_follower_followee".equalsIgnoreCase(violation.getConstraintName())) return true;
        }
        return false;
    }

    public FollowSummaryDto getSummary(UUID userId, UUID currentUserId) {
        user(userId);
        Follow mine = followRepository.findByFollowerIdAndFolloweeId(currentUserId, userId).orElse(null);
        return new FollowSummaryDto(userId, followRepository.countByFolloweeId(userId),
            followRepository.countByFollowerId(userId), mine != null, mine == null ? null : mine.getId(),
            followRepository.existsByFollowerIdAndFolloweeId(userId, currentUserId));
    }

    public FollowListResponse getList(UUID userId, boolean followings, String cursor,
        UUID idAfter, int limit, String nameLike) {
        if (limit < 1 || limit == Integer.MAX_VALUE) throw new OtbooException(FOLLOW_INVALID_CURSOR);
        user(userId);
        Instant after = null;
        try {
            if (cursor != null) after = Instant.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new OtbooException(FOLLOW_INVALID_CURSOR);
        }
        if (after == null && idAfter != null) {
            Follow anchor = followRepository.findById(idAfter)
                .orElseThrow(() -> new OtbooException(FOLLOW_INVALID_CURSOR));
            if (!userId.equals(followings ? anchor.getFollowerId() : anchor.getFolloweeId())) {
                throw new OtbooException(FOLLOW_INVALID_CURSOR);
            }
            after = anchor.getCreatedAt();
        }
        List<Follow> rows = followQueryRepository.findList(userId, followings, after, idAfter, limit + 1, nameLike);
        boolean hasNext = rows.size() > limit;
        List<FollowDto> data = rows.stream().limit(limit)
            .map(f -> new FollowDto(f.getId(), summary(user(f.getFolloweeId())),
                summary(user(f.getFollowerId()))))
            .toList();
        Follow last = hasNext ? rows.get(limit - 1) : null;
        long total = followQueryRepository.countList(userId, followings, nameLike);
        return new FollowListResponse(data, last == null ? null : last.getCreatedAt().toString(),
            last == null ? null : last.getId(), hasNext, total, "createdAt", SortDirection.DESCENDING);
    }

    @Transactional
    public void cancel(UUID followId, UUID currentUserId) {
        Follow follow = followRepository.findById(followId)
            .orElseThrow(() -> new OtbooException(FOLLOW_NOT_FOUND));
        if (!follow.getFollowerId().equals(currentUserId)) throw new OtbooException(ACCESS_DENIED);
        followRepository.delete(follow);
    }

    private User user(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new OtbooException(FOLLOW_USER_NOT_FOUND));
    }

    private UserSummary summary(User user) {
        String image = profileRepository.findByUserId(user.getId())
            .map(Profile::getProfileImageUrl).orElse(null);
        return new UserSummary(user.getId(), user.getName(), image);
    }
}
