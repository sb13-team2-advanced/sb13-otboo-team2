package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.entity.RefreshToken;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.repository.RefreshTokenRepository;
import codeit.ootd.domain.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issue(UUID sessionId) {

        if (refreshTokenRepository.findBySessionId(sessionId).isPresent()) {
            throw new IllegalStateException("이미 Refresh Token이 발급된 세션입니다.");
        }

        Instant now = clock.instant();

        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(() ->
                        new IllegalArgumentException("인증 세션이 존재하지 않습니다.")
                );

        if (!session.isActive(now)) {
            throw new IllegalArgumentException("유효하지 않은 인증 세션입니다.");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = hash(rawToken);
        Instant expiresAt = now.plus(REFRESH_TOKEN_TTL);

        RefreshToken refreshToken =
                RefreshToken.create(sessionId, tokenHash, expiresAt);

        refreshTokenRepository.save(refreshToken);

        log.debug("Refresh Token 발급 완료: sessionId={}", sessionId);

        return rawToken;
    }

    @Override
    @Transactional
    public String rotate(UUID sessionId, String oldRawToken) {

        RefreshToken existingToken = refreshTokenRepository
                .findBySessionIdForUpdate(sessionId)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException()
                );

        if (oldRawToken == null || oldRawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String oldTokenHash = hash(oldRawToken);

        if (!MessageDigest.isEqual(
                oldTokenHash.getBytes(StandardCharsets.UTF_8),
                existingToken.getTokenHash().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new InvalidRefreshTokenException();
        }

        Instant now = clock.instant();

        if (!existingToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!session.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String newRawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        existingToken.rotate(
                hash(newRawToken),
                now.plus(REFRESH_TOKEN_TTL)
        );

        log.debug("Refresh Token 교체 완료: sessionId={}", sessionId);

        return newRawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public UUID findSessionIdByToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException()
                );

        return refreshToken.getSessionId();
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.", e
            );
        }
    }
}