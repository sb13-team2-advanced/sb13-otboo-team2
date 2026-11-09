package codeit.ootd.domain.feed.service.impl;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedCommentService;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedCommentServiceImpl implements FeedCommentService {

    private final FeedRepository feedRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final UserRepository userRepository;
    private final FeedResponseAssembler feedResponseAssembler;

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

        return new CommentDto(
                comment.getId(),
                comment.getCreatedAt(),
                comment.getFeedId(),
                feedResponseAssembler.toAuthorDto(author),
                comment.getContent()
        );
    }
}