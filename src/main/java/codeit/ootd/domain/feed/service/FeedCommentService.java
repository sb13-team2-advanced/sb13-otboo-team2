package codeit.ootd.domain.feed.service;

import codeit.ootd.domain.feed.dto.request.CommentCreateRequest;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import java.util.UUID;

public interface FeedCommentService {

    CommentDto create(UUID feedId, CommentCreateRequest request, UUID userId);

    CommentDtoCursorResponse list(UUID feedId, String cursor, UUID idAfter, int limit);
}