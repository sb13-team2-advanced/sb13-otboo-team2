package codeit.ootd.domain.feed.service.impl;

import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedLike;
import codeit.ootd.domain.feed.repository.FeedLikeRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedLikeService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedLikeServiceImpl implements FeedLikeService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;

    @Override
    @Transactional
    public void like(UUID feedId, UUID userId) {
        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (feedId == null) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }

        Feed feed = feedRepository.findByIdForUpdate(feedId)
                .orElseThrow(
                        () -> new OtbooException(ErrorCode.FEED_NOT_FOUND)
                );

        if (feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)) {
            return;
        }

        feedLikeRepository.save(new FeedLike(feedId, userId));
        feed.increaseLikeCount();
    }

    @Override
    @Transactional
    public void unlike(UUID feedId, UUID userId) {
        if (userId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (feedId == null) {
            throw new OtbooException(ErrorCode.INVALID_REQUEST);
        }

        Feed feed = feedRepository.findByIdForUpdate(feedId)
                .orElseThrow(() -> new OtbooException(ErrorCode.FEED_NOT_FOUND));

        int deletedCount = feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId);
        if (deletedCount > 0) {
            feed.decreaseLikeCount();
        }
    }
}