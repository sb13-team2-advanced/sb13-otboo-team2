package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.entity.RefreshToken;
import codeit.ootd.domain.auth.exception.InvalidRefreshTokenException;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.repository.RefreshTokenRepository;
import codeit.ootd.domain.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private Clock clock;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    @DisplayName("Refresh Token 발급 시 원본 토큰을 반환하고 해시를 DB에 저장한다")
    void issue_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        AuthSession activeSession = AuthSession.create(
                UUID.randomUUID(),
                now.plus(Duration.ofDays(30))
        );

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(activeSession));

        when(clock.instant()).thenReturn(now);

        // when
        String rawToken = refreshTokenService.issue(sessionId);

        // then
        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();

        assertAll(
                () -> assertEquals(sessionId, savedToken.getSessionId()),
                () -> assertNotEquals(rawToken, savedToken.getTokenHash()),
                () -> assertEquals(
                        sha256(rawToken),
                        savedToken.getTokenHash()
                ),
                () -> assertEquals(
                        now.plus(Duration.ofDays(7)),
                        savedToken.getExpiresAt()
                )
        );
    }

    @Test
    @DisplayName("이미 Refresh Token이 존재하는 세션에는 신규 발급하지 않는다")
    void issue_existingSession_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                "existing-token-hash",
                Instant.parse("2026-11-01T00:00:00Z")
        );

        when(refreshTokenRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(existingToken));

        // when & then
        assertThrows(
                IllegalStateException.class,
                () -> refreshTokenService.issue(sessionId)
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 재발급하면 기존 토큰이 교체된다")
    void rotate_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        String oldRawToken = "old-refresh-token";
        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        String oldHash = sha256(oldRawToken);

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                oldHash,
                now.plus(Duration.ofDays(1))
        );

        when(clock.instant()).thenReturn(now);
        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        AuthSession session = AuthSession.create(
                UUID.randomUUID(),
                now.plus(Duration.ofDays(30))
        );
        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when
        String newRawToken = refreshTokenService.rotate(
                sessionId,
                oldRawToken
        );

        // then
        assertAll(
                () -> assertNotNull(newRawToken),
                () -> assertNotEquals(oldRawToken, newRawToken),
                () -> assertEquals(
                        sha256(newRawToken),
                        existingToken.getTokenHash()
                ),
                () -> assertEquals(
                        now.plus(Duration.ofDays(7)),
                        existingToken.getExpiresAt()
                )
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refresh Token이 일치하지 않으면 재발급할 수 없다")
    void rotate_invalidToken_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                sha256("valid-refresh-token"),
                now.plus(Duration.ofDays(1))
        );

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        // when & then
        assertAll(
                () -> assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.rotate(
                                sessionId,
                                "invalid-refresh-token"
                        )
                ),
                () -> assertEquals(
                        sha256("valid-refresh-token"),
                        existingToken.getTokenHash()
                ),
                () -> assertEquals(
                        now.plus(Duration.ofDays(1)),
                        existingToken.getExpiresAt()
                )
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 Refresh Token으로는 재발급할 수 없다")
    void rotate_expiredToken_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();
        String oldToken = "expired-refresh-token";

        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        Instant expiredAt = now.minus(Duration.ofSeconds(1));

        String oldHash = sha256(oldToken);

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                oldHash,
                expiredAt
        );

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        when(clock.instant()).thenReturn(now);

        // when & then
        assertAll(
                () -> assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.rotate(sessionId, oldToken)
                ),
                () -> assertEquals(oldHash, existingToken.getTokenHash()),
                () -> assertEquals(expiredAt, existingToken.getExpiresAt())
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refresh Token이 존재하지 않으면 재발급할 수 없다")
    void rotate_tokenNotFound_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();
        String oldToken = "old-refresh-token";

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(sessionId, oldToken)
        );

        verify(refreshTokenRepository)
                .findBySessionIdForUpdate(sessionId);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("무효화된 세션에서는 Refresh Token을 재발급할 수 없다")
    void rotate_revokedSession_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        String oldToken = "valid-refresh-token";
        String oldHash = sha256(oldToken);

        AuthSession session = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(30))
        );
        session.revoke(now.minus(Duration.ofMinutes(10)));

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                oldHash,
                now.plus(Duration.ofDays(7))
        );

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        when(clock.instant()).thenReturn(now);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(sessionId, oldToken)
        );

        assertEquals(oldHash, existingToken.getTokenHash());

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 인증 세션에서는 Refresh Token을 재발급할 수 없다")
    void rotate_expiredSession_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        String oldToken = "valid-refresh-token";
        String oldHash = sha256(oldToken);

        // Refresh Token 자체는 유효함
        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                oldHash,
                now.plus(Duration.ofDays(7))
        );

        // AuthSession은 이미 만료됨
        AuthSession expiredSession = AuthSession.create(
                userId,
                now.minus(Duration.ofMinutes(1))
        );

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        when(clock.instant()).thenReturn(now);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(expiredSession));

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(sessionId, oldToken)
        );

        assertEquals(oldHash, existingToken.getTokenHash());
        assertEquals(
                now.plus(Duration.ofDays(7)),
                existingToken.getExpiresAt()
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증 세션이 존재하지 않으면 Refresh Token을 재발급할 수 없다")
    void rotate_sessionNotFound_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        String oldToken = "valid-refresh-token";
        String oldHash = sha256(oldToken);
        Instant expiresAt = now.plus(Duration.ofDays(7));

        RefreshToken existingToken = RefreshToken.create(
                sessionId,
                oldHash,
                expiresAt
        );

        when(refreshTokenRepository.findBySessionIdForUpdate(sessionId))
                .thenReturn(Optional.of(existingToken));

        when(clock.instant()).thenReturn(now);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate(sessionId, oldToken)
        );

        assertEquals(oldHash, existingToken.getTokenHash());
        assertEquals(expiresAt, existingToken.getExpiresAt());

        verify(authSessionRepository).findById(sessionId);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("무효화된 인증 세션에는 Refresh Token을 발급할 수 없다")
    void issue_revokedSession_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        AuthSession session = AuthSession.create(
                userId,
                now.plus(Duration.ofDays(30))
        );
        session.revoke(now.minus(Duration.ofMinutes(10)));

        when(refreshTokenRepository.findBySessionId(sessionId))
                .thenReturn(Optional.empty());

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session));

        when(clock.instant()).thenReturn(now);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.issue(sessionId)
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 인증 세션에는 Refresh Token을 발급할 수 없다")
    void issue_expiredSession_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        AuthSession expiredSession = AuthSession.create(
                userId,
                now.minus(Duration.ofMinutes(1))
        );

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(expiredSession));

        when(clock.instant()).thenReturn(now);

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.issue(sessionId)
        );

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("인증 세션이 존재하지 않으면 Refresh Token을 발급할 수 없다")
    void issue_sessionNotFound_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        when(clock.instant()).thenReturn(now);

        when(authSessionRepository.findById(sessionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService.issue(sessionId)
        );

        verify(authSessionRepository).findById(sessionId);

        verify(refreshTokenRepository, never()).save(any());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("유효한 Refresh Token으로 세션 ID를 조회한다")
    void findSessionIdByToken_success() throws NoSuchAlgorithmException {
        // given
        UUID sessionId = UUID.randomUUID();
        String rawToken = "test-refresh-token";

        String tokenHash = sha256(rawToken);

        RefreshToken refreshToken = mock(RefreshToken.class);

        when(refreshToken.getSessionId()).thenReturn(sessionId);
        when(refreshTokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(refreshToken));

        // when
        UUID result = refreshTokenService.findSessionIdByToken(rawToken);

        // then
        assertEquals(sessionId, result);
        verify(refreshTokenRepository).findByTokenHash(tokenHash);
    }

    @Test
    @DisplayName("존재하지 않는 Refresh Token으로 세션 조회 시 예외가 발생한다")
    void findSessionIdByToken_notFound() {
        // given
        String rawToken = "invalid-refresh-token";

        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.findSessionIdByToken(rawToken)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("Refresh Token이 비어 있으면 세션 조회를 거부한다")
    void findSessionIdByToken_blankToken(String rawToken) {
        // when & then
        assertThrows(
                InvalidRefreshTokenException.class,
                () -> refreshTokenService.findSessionIdByToken(rawToken)
        );

        verify(refreshTokenRepository, never())
                .findByTokenHash(anyString());
    }

}