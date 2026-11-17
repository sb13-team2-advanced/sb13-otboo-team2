package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedSearchRequest;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.FeedDtoCursorResponse;
import java.util.UUID;

public interface FeedService {

    FeedDto create(FeedCreateRequest request, UUID userId);

    FeedDtoCursorResponse list(FeedSearchRequest request, UUID userId);

    FeedDto update(UUID feedId, FeedUpdateRequest request, UUID userId);

    void delete(UUID feedId, UUID userId);
}