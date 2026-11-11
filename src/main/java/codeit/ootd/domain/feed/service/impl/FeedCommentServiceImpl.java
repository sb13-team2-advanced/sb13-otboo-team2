package codeit.ootd.domain.feed.service.impl;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.event.NotificationRequestedEvent;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedCommentServiceImpl implements FeedCommentService {

    private final FeedRepository feedRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final UserRepository userRepository;
    private final FeedResponseAssembler feedResponseAssembler;
    private final ProfileRepository profileRepository;
    private final FeedMapper feedMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CommentDto create(UUID feedId, CommentCreateRequest request, UUID userId) {
        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (request == null || request.authorId() == null) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }
        if (!userId.equals(request.authorId())) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }
        if (feedId == null || !feedId.equals(request.feedId())
                || request.content() == null || request.content().isBlank()) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }

        Feed feed = feedRepository.findByIdForUpdate(feedId)
                .orElseThrow(() -> new OtbooException(ErrorCode.FEED_NOT_FOUND));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new OtbooException(ErrorCode.FEED_AUTHOR_NOT_FOUND));

        FeedComment comment = feedCommentRepository.save(
                new FeedComment(feedId, userId, request.content())
        );
        feed.increaseCommentCount();

        eventPublisher.publishEvent(new NotificationRequestedEvent(
                feed.getAuthorId(),
                "피드에 댓글이 추가되었습니다.",
                "회원님의 피드에 새로운 댓글이 등록되었습니다.",
                NotificationLevel.INFO
        ));

        return toCommentDto(comment, feedResponseAssembler.toAuthorDto(author));
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDtoCursorResponse list(UUID feedId, String cursor, UUID idAfter, int limit) {
        if (feedId == null || limit <= 0 || limit == Integer.MAX_VALUE) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }

        Instant cursorTime = parseCursor(cursor, idAfter);
        PageRequest pageRequest = PageRequest.of(0, limit + 1);
        List<FeedComment> rows = cursorTime == null
                ? feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(feedId, pageRequest)
                : feedCommentRepository.findNextPage(feedId, cursorTime, idAfter, pageRequest);

        boolean hasNext = rows.size() > limit;
        List<FeedComment> page = hasNext ? rows.subList(0, limit) : rows;
        FeedComment last = hasNext ? page.get(page.size() - 1) : null;

        return new CommentDtoCursorResponse(
                toCommentDtos(page),
                last == null ? null : last.getCreatedAt().toString(),
                last == null ? null : last.getId(),
                hasNext,
                feedCommentRepository.countByFeedId(feedId),
                "createdAt",
                SortDirection.DESCENDING
        );
    }

    private Instant parseCursor(String cursor, UUID idAfter) {
        if (cursor == null && idAfter == null) {
            return null;
        }
        if (cursor == null || idAfter == null) {
            throw new OtbooException(ErrorCode.FEED_INVALID_CURSOR);
        }
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new OtbooException(ErrorCode.FEED_INVALID_CURSOR);
        }
    }

    private List<CommentDto> toCommentDtos(List<FeedComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }

        List<UUID> authorIds = comments.stream()
                .map(FeedComment::getAuthorId)
                .distinct()
                .toList();

        Map<UUID, Profile> profilesByUserId = profileRepository
                .findByUserIdIn(authorIds)
                .stream()
                .collect(Collectors.toMap(
                        Profile::getUserId,
                        Function.identity()
                ));

        Map<UUID, AuthorDto> authorsById = userRepository
                .findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        author -> {
                            Profile profile = profilesByUserId.get(author.getId());
                            String profileImageUrl = profile == null
                                    ? null
                                    : profile.getProfileImageUrl();
                            return feedMapper.toAuthorDto(author, profileImageUrl);
                        }
                ));

        return comments.stream()
                .map(comment -> {
                    AuthorDto author = authorsById.get(comment.getAuthorId());
                    if (author == null) {
                        throw new OtbooException(ErrorCode.FEED_AUTHOR_NOT_FOUND);
                    }
                    return toCommentDto(comment, author);
                })
                .toList();
    }

    private CommentDto toCommentDto(FeedComment comment, AuthorDto author) {
        return new CommentDto(
                comment.getId(),
                comment.getCreatedAt(),
                comment.getFeedId(),
                author,
                comment.getContent()
        );
    }
}