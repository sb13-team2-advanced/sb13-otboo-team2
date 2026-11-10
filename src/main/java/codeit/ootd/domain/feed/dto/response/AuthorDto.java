package codeit.ootd.domain.feed.dto.response;

import java.util.UUID;

public record AuthorDto(
        UUID userId,
        String name,
        String profileImageUrl
) {
}