package codeit.ootd.domain.auth.repository;

import codeit.ootd.domain.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    // 사용자의 만료되지 않은 활성 세션 조회
    List<AuthSession> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId,
            Instant now
    );
}