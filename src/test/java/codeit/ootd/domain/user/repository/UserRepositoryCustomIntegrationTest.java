package codeit.ootd.domain.user.repository;

import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryCustomIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("첫 페이지 조회 시 사용자를 이메일 오름차순으로 반환한다")
    void findUsers_firstPage_sortedByEmailAscending() {
        // given
        User userB = User.create(
                "사용자B", "bbb@example.com", "encoded-password"
        );

        User userA = User.create(
                "사용자A", "aaa@example.com", "encoded-password"
        );

        userRepository.saveAllAndFlush(List.of(userB, userA));

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(2, result.getContent().size());
        assertEquals(
                "aaa@example.com",
                result.getContent().get(0).getEmail()
        );
        assertEquals(
                "bbb@example.com",
                result.getContent().get(1).getEmail()
        );
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("조회 결과가 limit보다 많으면 hasNext가 true이고 limit만큼 반환한다")
    void findUsers_firstPage_hasNext() {
        // given
        User userC = User.create(
                "사용자C", "ccc@example.com", "encoded-password"
        );
        User userA = User.create(
                "사용자A", "aaa@example.com", "encoded-password"
        );
        User userB = User.create(
                "사용자B", "bbb@example.com", "encoded-password"
        );

        userRepository.saveAllAndFlush(
                List.of(userC, userA, userB)
        );

        // when
        var result = userRepository.findUsers(
                null,
                null,
                2,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(2, result.getContent().size());
        assertEquals(
                "aaa@example.com",
                result.getContent().get(0).getEmail()
        );
        assertEquals(
                "bbb@example.com",
                result.getContent().get(1).getEmail()
        );
        assertTrue(result.hasNext());
    }


    @Test
    @DisplayName("이메일 내림차순 조회 시 이메일 역순으로 반환한다")
    void findUsers_sortedByEmailDescending() {
        // given
        User userA = User.create(
                "사용자A", "aaa@example.com", "encoded-password"
        );
        User userB = User.create(
                "사용자B", "bbb@example.com", "encoded-password"
        );
        User userC = User.create(
                "사용자C", "ccc@example.com", "encoded-password"
        );

        userRepository.saveAllAndFlush(
                List.of(userA, userB, userC)
        );

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "email",
                "DESCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(3, result.getContent().size());
        assertEquals(
                "ccc@example.com",
                result.getContent().get(0).getEmail()
        );
        assertEquals(
                "bbb@example.com",
                result.getContent().get(1).getEmail()
        );
        assertEquals(
                "aaa@example.com",
                result.getContent().get(2).getEmail()
        );
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("생성일 오름차순 조회 시 오래된 사용자부터 반환한다")
    void findUsers_sortedByCreatedAtAscending() {
        // given
        User olderUser = User.create(
                "오래된 사용자", "zzz@example.com", "encoded-password"
        );
        User newerUser = User.create(
                "최근 사용자", "aaa@example.com", "encoded-password"
        );

        userRepository.saveAndFlush(olderUser);
        userRepository.saveAndFlush(newerUser);

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "createdAt",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(2, result.getContent().size());
        assertEquals(
                olderUser.getId(),
                result.getContent().get(0).getId()
        );
        assertEquals(
                newerUser.getId(),
                result.getContent().get(1).getId()
        );
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("생성일이 동일하면 UUID 오름차순으로 정렬한다")
    void findUsers_sameCreatedAt_sortedByIdAscending() {
        // given
        User userA = User.create(
                "사용자A", "aaa@example.com", "encoded-password"
        );
        User userB = User.create(
                "사용자B", "bbb@example.com", "encoded-password"
        );

        userRepository.saveAllAndFlush(List.of(userA, userB));

        Instant sameCreatedAt = Instant.parse("2026-10-30T00:00:00Z");

        // JPQL 벌크 업데이트로 두 사용자의 생성 시각을 동일하게 설정
        entityManager.createQuery("""
            UPDATE User u
            SET u.createdAt = :createdAt
            WHERE u.id IN :ids
            """)
                .setParameter("createdAt", sameCreatedAt)
                .setParameter("ids", List.of(userA.getId(), userB.getId()))
                .executeUpdate();

        entityManager.clear();

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "createdAt",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        List<UUID> expectedIds = List.of(userA.getId(), userB.getId())
                .stream()
                .sorted((a, b) -> a.toString().compareTo(b.toString()))
                .toList();

        List<UUID> actualIds = result.getContent().stream()
                .map(User::getId)
                .toList();

        assertEquals(expectedIds, actualIds);
    }

    @Test
    @DisplayName("이메일 오름차순에서 커서를 전달하면 다음 사용자부터 조회한다")
    void findUsers_emailAscending_withCursor_returnsNextPage() {
        // given
        User userA = User.create("사용자A", "aaa@example.com", "encoded-password");
        User userB = User.create("사용자B", "bbb@example.com", "encoded-password");
        User userC = User.create("사용자C", "ccc@example.com", "encoded-password");

        userRepository.saveAllAndFlush(List.of(userA, userB, userC));

        // when
        var result = userRepository.findUsers(
                "bbb@example.com",
                userB.getId(),
                2,
                "email",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(1, result.getContent().size());
        assertEquals("ccc@example.com", result.getContent().get(0).getEmail());
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("이메일 내림차순에서 커서를 전달하면 다음 사용자부터 조회한다")
    void findUsers_emailDescending_withCursor_returnsNextPage() {
        // given
        User userA = User.create("사용자A", "aaa@example.com", "encoded-password");
        User userB = User.create("사용자B", "bbb@example.com", "encoded-password");
        User userC = User.create("사용자C", "ccc@example.com", "encoded-password");

        userRepository.saveAllAndFlush(List.of(userA, userB, userC));

        // when
        var result = userRepository.findUsers(
                "bbb@example.com",
                userB.getId(),
                2,
                "email",
                "DESCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(1, result.getContent().size());
        assertEquals("aaa@example.com", result.getContent().get(0).getEmail());
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("생성일 오름차순에서 커서를 전달하면 다음 사용자부터 조회한다")
    void findUsers_createdAtAscending_withCursor_returnsNextPage() {
        // given
        User userA = User.create("사용자A", "aaa@example.com", "encoded-password");
        User userB = User.create("사용자B", "bbb@example.com", "encoded-password");
        User userC = User.create("사용자C", "ccc@example.com", "encoded-password");

        userRepository.saveAllAndFlush(List.of(userA, userB, userC));

        Instant timeA = Instant.parse("2026-10-28T10:00:00Z");
        Instant timeB = Instant.parse("2026-10-29T10:00:00Z");
        Instant timeC = Instant.parse("2026-10-30T10:00:00Z");

        entityManager.createQuery("""
            UPDATE User u
            SET u.createdAt = CASE
                WHEN u.id = :idA THEN :timeA
                WHEN u.id = :idB THEN :timeB
                ELSE :timeC
            END
            WHERE u.id IN :ids
            """)
                .setParameter("idA", userA.getId())
                .setParameter("idB", userB.getId())
                .setParameter("timeA", timeA)
                .setParameter("timeB", timeB)
                .setParameter("timeC", timeC)
                .setParameter("ids", List.of(userA.getId(), userB.getId(), userC.getId()))
                .executeUpdate();

        entityManager.clear();

        // when
        var result = userRepository.findUsers(
                timeB.toString(),
                userB.getId(),
                2,
                "createdAt",
                "ASCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(1, result.getContent().size());
        assertEquals(userC.getId(), result.getContent().get(0).getId());
        assertFalse(result.hasNext());
    }


    @Test
    @DisplayName("생성일 내림차순에서 커서를 전달하면 다음 사용자부터 조회한다")
    void findUsers_createdAtDescending_withCursor_returnsNextPage() {
        // given
        User userA = User.create("사용자A", "aaa@example.com", "encoded-password");
        User userB = User.create("사용자B", "bbb@example.com", "encoded-password");
        User userC = User.create("사용자C", "ccc@example.com", "encoded-password");

        userRepository.saveAllAndFlush(List.of(userA, userB, userC));

        Instant timeA = Instant.parse("2026-10-28T10:00:00Z");
        Instant timeB = Instant.parse("2026-10-29T10:00:00Z");
        Instant timeC = Instant.parse("2026-10-30T10:00:00Z");

        entityManager.createQuery("""
            UPDATE User u
            SET u.createdAt = CASE
                WHEN u.id = :idA THEN :timeA
                WHEN u.id = :idB THEN :timeB
                ELSE :timeC
            END
            WHERE u.id IN :ids
            """)
                .setParameter("idA", userA.getId())
                .setParameter("idB", userB.getId())
                .setParameter("timeA", timeA)
                .setParameter("timeB", timeB)
                .setParameter("timeC", timeC)
                .setParameter("ids", List.of(userA.getId(), userB.getId(), userC.getId()))
                .executeUpdate();

        entityManager.clear();

        // when
        var result = userRepository.findUsers(
                timeB.toString(),
                userB.getId(),
                2,
                "createdAt",
                "DESCENDING",
                null,
                null,
                null
        );

        // then
        assertEquals(1, result.getContent().size());
        assertEquals(userA.getId(), result.getContent().get(0).getId());
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("이메일 부분 검색 시 검색어가 포함된 사용자만 조회한다")
    void findUsers_emailLike_returnsMatchingUsers() {
        // given
        User userA = User.create(
                "사용자A", "aaa@gmail.com", "encoded-password"
        );
        User userB = User.create(
                "사용자B", "bbb@naver.com", "encoded-password"
        );
        User userC = User.create(
                "사용자C", "ccc@gmail.com", "encoded-password"
        );

        userRepository.saveAllAndFlush(List.of(userA, userB, userC));

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "email",
                "ASCENDING",
                "gmail",
                null,
                null
        );

        // then
        assertEquals(2, result.getContent().size());
        assertEquals("aaa@gmail.com", result.getContent().get(0).getEmail());
        assertEquals("ccc@gmail.com", result.getContent().get(1).getEmail());
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("사용자 권한 필터 조회 시 해당 권한의 사용자만 반환한다")
    void findUsers_roleEqual_returnsMatchingUsers() {
        // given
        User admin = User.create(
                "관리자", "admin@example.com", "encoded-password"
        );
        User normalUser = User.create(
                "일반 사용자", "user@example.com", "encoded-password"
        );

        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        userRepository.saveAllAndFlush(List.of(admin, normalUser));

        // when
        var result = userRepository.findUsers(
                null,
                null,
                10,
                "email",
                "ASCENDING",
                null,
                UserRole.ADMIN,
                null
        );

        // then
        assertEquals(1, result.getContent().size());
        assertEquals(
                "admin@example.com",
                result.getContent().get(0).getEmail()
        );
        assertEquals(
                UserRole.ADMIN,
                result.getContent().get(0).getRole()
        );
        assertFalse(result.hasNext());
    }











}
