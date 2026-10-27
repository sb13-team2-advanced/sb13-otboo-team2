package codeit.ootd.domain.directmessage.dto;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id,
    Instant createdAt,
    // UserSummary sender,
    // UserSummary receiver,
    String content
) {
}
