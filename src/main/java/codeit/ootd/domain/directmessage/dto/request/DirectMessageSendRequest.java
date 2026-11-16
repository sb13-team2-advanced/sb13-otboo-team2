package codeit.ootd.domain.directmessage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DirectMessageSendRequest(
        @NotNull UUID senderId,
        @NotNull UUID receiverId,
        @NotBlank String content
) {
}
