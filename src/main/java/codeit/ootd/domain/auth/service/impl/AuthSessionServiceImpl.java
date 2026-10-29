package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.exception.InvalidAuthSessionException;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.service.AuthSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    private static final Duration SESSION_DURATION = Duration.ofDays(7);

    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;


    @Override
    @Transactional
    public AuthSession createSession(UUID userId) {
        Instant now = clock.instant();

        // 1. 기존 활성 세션 조회
        List<AuthSession> activeSessions =
                authSessionRepository
                        .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                                userId, now
                        );

        // 2. 기존 활성 세션 무효화
        for (AuthSession activeSession : activeSessions) {
            activeSession.revoke(now);
        }

        // 3. 새로운 세션 생성
        AuthSession newSession = AuthSession.create(
                userId,
                now.plus(SESSION_DURATION)
        );

        // 4. 새로운 세션 저장
        AuthSession savedSession =
                authSessionRepository.save(newSession);

        log.debug("인증 세션 생성 완료: userId={}, revokedSessionCount={}",
                userId, activeSessions.size());

        return savedSession;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthSession getActiveSession(UUID sessionId) {
        Instant now = clock.instant();

        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(InvalidAuthSessionException::new);

        if (!session.isActive(now)) {
            throw new InvalidAuthSessionException();
        }

        return session;
    }

    @Override
    @Transactional
    public void revokeSession(UUID sessionId) {
        Instant now = clock.instant();

        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(InvalidAuthSessionException::new);

        session.revoke(now);

        log.debug("인증 세션 폐기 완료: sessionId={}", sessionId);
    }

}