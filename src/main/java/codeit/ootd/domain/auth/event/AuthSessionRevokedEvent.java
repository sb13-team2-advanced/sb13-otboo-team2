package codeit.ootd.domain.auth.event;

import java.util.UUID;

public record AuthSessionRevokedEvent(UUID sessionId) {
}
