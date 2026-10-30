package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.AuthSession;

import java.util.UUID;

public interface AuthSessionService {

    AuthSession createSession(UUID userId);

    AuthSession getActiveSession(UUID sessionId);

    void revokeSession(UUID sessionId);

}