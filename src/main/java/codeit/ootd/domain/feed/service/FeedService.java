package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import java.util.UUID;

public interface FeedService {

    FeedDto create(FeedCreateRequest request, UUID userId);
}