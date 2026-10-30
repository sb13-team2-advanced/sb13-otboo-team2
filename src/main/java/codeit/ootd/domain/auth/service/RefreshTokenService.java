package codeit.ootd.domain.auth.service;

import java.util.UUID;

public interface RefreshTokenService {

    String issue(UUID sessionId);

    String rotate(UUID sessionId, String oldRawToken);

    UUID findSessionIdByToken(String rawToken);
}
