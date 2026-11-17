package codeit.ootd.domain.directmessage.dto;

import codeit.ootd.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {
}
