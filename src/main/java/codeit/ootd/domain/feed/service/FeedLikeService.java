package codeit.ootd.domain.feed.service;

import java.util.UUID;

public interface FeedLikeService {

    void like(UUID feedId, UUID userId);
}