package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import java.util.UUID;

public interface FeedCommentService {

    CommentDto create(UUID feedId, CommentCreateRequest request, UUID userId);
}