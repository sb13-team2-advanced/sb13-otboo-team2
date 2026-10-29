package codeit.ootd.domain.auth.repository;

import codeit.ootd.domain.auth.entity.RefreshToken;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Refresh Token을 저장하고 ID로 조회할 수 있다")
    void saveAndFindById_success() {
        // given
        UUID sessionId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-11-01T00:00:00Z");

        RefreshToken refreshToken = RefreshToken.create(
                sessionId,
                "hashed-refresh-token",
                expiresAt
        );

        // when
        RefreshToken saved = refreshTokenRepository.saveAndFlush(refreshToken);

        // then
        RefreshToken found = refreshTokenRepository.findById(saved.getId())
                .orElseThrow();

        assertAll(
                () -> assertNotNull(found.getId()),
                () -> assertEquals(sessionId, found.getSessionId()),
                () -> assertEquals("hashed-refresh-token", found.getTokenHash()),
                () -> assertEquals(expiresAt, found.getExpiresAt())
        );
    }

    @Test
    @DisplayName("세션 ID로 Refresh Token을 조회할 수 있다")
    void findBySessionId_success() {
        // given
        UUID sessionId = UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.create(
                sessionId,
                "hashed-refresh-token",
                Instant.parse("2026-11-01T00:00:00Z")
        );

        refreshTokenRepository.saveAndFlush(refreshToken);

        // when
        RefreshToken found = refreshTokenRepository.findBySessionId(sessionId)
                .orElseThrow();

        // then
        assertAll(
                () -> assertEquals(sessionId, found.getSessionId()),
                () -> assertEquals(refreshToken.getId(), found.getId())
        );
    }

    @Test
    @DisplayName("동일한 세션 ID로 Refresh Token을 중복 저장할 수 없다")
    void save_duplicateSessionId_throwsException() {
        // given
        UUID sessionId = UUID.randomUUID();

        RefreshToken firstToken = RefreshToken.create(
                sessionId,
                "first-token-hash",
                Instant.parse("2026-11-01T00:00:00Z")
        );

        RefreshToken secondToken = RefreshToken.create(
                sessionId,
                "second-token-hash",
                Instant.parse("2026-11-02T00:00:00Z")
        );

        refreshTokenRepository.saveAndFlush(firstToken);

        // when & then
        assertThrows(
                DataIntegrityViolationException.class,
                () -> refreshTokenRepository.saveAndFlush(secondToken)
        );
    }

    @Test
    @DisplayName("Refresh Token 갱신 시 해시와 만료 시간이 변경된다")
    void rotate_success() {
        // given
        UUID sessionId = UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.create(
                sessionId,
                "old-token-hash",
                Instant.parse("2026-11-01T00:00:00Z")
        );

        String newTokenHash = "new-token-hash";
        Instant newExpiresAt = Instant.parse("2026-11-08T00:00:00Z");

        // when
        refreshToken.rotate(newTokenHash, newExpiresAt);

        // then
        assertAll(
                () -> assertEquals(sessionId, refreshToken.getSessionId()),
                () -> assertEquals(newTokenHash, refreshToken.getTokenHash()),
                () -> assertEquals(newExpiresAt, refreshToken.getExpiresAt())
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("갱신할 토큰 해시가 유효하지 않으면 예외가 발생한다")
    void rotate_invalidTokenHash_throwsException(String invalidHash) {
        // given
        RefreshToken refreshToken = RefreshToken.create(
                UUID.randomUUID(),
                "old-token-hash",
                Instant.parse("2026-11-01T00:00:00Z")
        );

        Instant newExpiresAt = Instant.parse("2026-11-08T00:00:00Z");

        // when & then
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshToken.rotate(invalidHash, newExpiresAt)
                ),
                () -> assertEquals("old-token-hash", refreshToken.getTokenHash()),
                () -> assertEquals(
                        Instant.parse("2026-11-01T00:00:00Z"),
                        refreshToken.getExpiresAt()
                )
        );
    }

    @Test
    @DisplayName("갱신할 만료 시간이 null이면 예외가 발생하고 기존 값이 유지된다")
    void rotate_nullExpiresAt_throwsException() {
        // given
        String oldTokenHash = "old-token-hash";
        Instant oldExpiresAt = Instant.parse("2026-11-01T00:00:00Z");

        RefreshToken refreshToken = RefreshToken.create(
                UUID.randomUUID(),
                oldTokenHash,
                oldExpiresAt
        );

        // when & then
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshToken.rotate("new-token-hash", null)
                ),
                () -> assertEquals(
                        oldTokenHash,
                        refreshToken.getTokenHash()
                ),
                () -> assertEquals(
                        oldExpiresAt,
                        refreshToken.getExpiresAt()
                )
        );
    }

    @Test
    @DisplayName("Refresh Token 갱신 시 변경된 해시와 만료 시간이 DB에 반영된다")
    void rotate_dirtyChecking_success() {
        // given
        UUID sessionId = UUID.randomUUID();

        Instant oldExpiresAt = Instant.parse("2026-11-01T00:00:00Z");
        Instant newExpiresAt = Instant.parse("2026-11-08T00:00:00Z");

        RefreshToken refreshToken = RefreshToken.create(
                sessionId,
                "old-token-hash",
                oldExpiresAt
        );

        refreshTokenRepository.saveAndFlush(refreshToken);

        UUID tokenId = refreshToken.getId();

        entityManager.clear();

        // when
        RefreshToken savedToken = refreshTokenRepository.findById(tokenId)
                .orElseThrow();

        savedToken.rotate("new-token-hash", newExpiresAt);

        entityManager.flush();
        entityManager.clear();

        // then
        RefreshToken updatedToken = refreshTokenRepository.findById(tokenId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(tokenId, updatedToken.getId()),
                () -> assertEquals(sessionId, updatedToken.getSessionId()),
                () -> assertEquals("new-token-hash", updatedToken.getTokenHash()),
                () -> assertEquals(newExpiresAt, updatedToken.getExpiresAt())
        );
    }

    @Test
    @DisplayName("존재하지 않는 세션 ID로 조회하면 빈 Optional을 반환한다")
    void findBySessionId_notFound() {
        // given
        UUID unknownSessionId = UUID.randomUUID();

        // when
        Optional<RefreshToken> result =
                refreshTokenRepository.findBySessionId(unknownSessionId);

        // then
        assertTrue(result.isEmpty());
    }

}