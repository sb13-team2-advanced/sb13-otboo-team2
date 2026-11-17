package codeit.ootd.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.service.NotificationService;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification-query-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
@Transactional
@DisplayName("알림 조회 및 읽음 처리 통합 테스트")
class NotificationQueryRepositoryIntegrationTest {

    @Autowired NotificationRepository repository;
    @Autowired NotificationService service;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    private final UUID receiverId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-11-05T00:00:00Z");

    @BeforeEach
    void setUp() {
        for (int index = 0; index < 6; index++) {
            Notification saved = repository.saveAndFlush(new Notification(
                    index < 5 ? receiverId : UUID.randomUUID(), "알림", "내용", NotificationLevel.INFO));
            entityManager.createNativeQuery("UPDATE notifications SET created_at = :time WHERE id = :id")
                    .setParameter("time", index == 0 ? createdAt.plusSeconds(1) : createdAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
        }
        entityManager.clear();
    }

    @Test
    @DisplayName("생성 시간이 같은 알림도 중복과 누락 없이 조회하고 수신자의 전체 개수를 반환한다")
    void paginatesWithoutDuplicatesAndCountsOnlyReceiversNotifications() {
        List<UUID> expected = entityManager.createQuery("""
                select n.id from Notification n where n.receiverId = :receiverId
                order by n.createdAt desc, n.id desc
                """, UUID.class).setParameter("receiverId", receiverId).getResultList();
        List<UUID> actual = new ArrayList<>();
        String cursor = null;
        UUID idAfter = null;
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            entityManager.clear();
            statistics.clear();
            var page = service.getNotifications(receiverId, cursor, idAfter, 2);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(page.totalCount()).isEqualTo(5L);
            assertThat(page.sortBy()).isEqualTo("createdAt");
            assertThat(page.sortDirection()).isEqualTo(SortDirection.DESCENDING);
            assertThat(page.data()).allSatisfy(dto -> {
                assertThat(dto.receiverId()).isEqualTo(receiverId);
                assertThat(dto.title()).isEqualTo("알림");
                assertThat(dto.content()).isEqualTo("내용");
                assertThat(dto.level()).isEqualTo(NotificationLevel.INFO);
                assertThat(dto.createdAt()).isEqualTo(dto.id().equals(expected.get(0))
                        ? createdAt.plusSeconds(1) : createdAt);
            });
            assertThat(page.hasNext()).isEqualTo(pageIndex < 2);
            if (page.hasNext()) {
                var last = page.data().get(page.data().size() - 1);
                assertThat(page.nextCursor()).isEqualTo(last.createdAt().toString());
                assertThat(page.nextIdAfter()).isEqualTo(last.id());
            }
            actual.addAll(page.data().stream().map(dto -> dto.id()).toList());
            cursor = page.nextCursor();
            idAfter = page.nextIdAfter();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(cursor).isNull();
        assertThat(idAfter).isNull();
    }

    @Test
    @DisplayName("조회 개수가 늘어도 목록과 전체 개수 조회에 SQL 두 번만 실행한다")
    void queryCountDoesNotGrowWithPageSize() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int limit : List.of(1, 5)) {
            entityManager.clear();
            statistics.clear();
            var page = service.getNotifications(receiverId, null, null, limit);
            assertThat(page.data()).hasSize(limit);
            // 목록과 전체 개수만 조회하며 DTO 변환에서 추가 SELECT가 발생하지 않는다.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("커서 기준 알림을 읽어 삭제해도 다음 페이지를 조회하고 재차 읽으면 거부한다")
    void readingAnchorDeletesItAndNextPageStillWorks() {
        var first = service.getNotifications(receiverId, null, null, 2);
        UUID anchorId = first.nextIdAfter();
        List<UUID> expected = service.getNotifications(
                receiverId, first.nextCursor(), anchorId, 10).data().stream()
                .map(dto -> dto.id()).toList();

        service.readNotification(anchorId, receiverId);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsById(anchorId)).isFalse();
        var next = service.getNotifications(receiverId, first.nextCursor(), anchorId, 10);
        assertThat(next.data().stream().map(dto -> dto.id()).toList()).containsExactlyElementsOf(expected);
        assertThat(next.totalCount()).isEqualTo(4L);
        assertThat(assertThrows(OtbooException.class,
                () -> service.readNotification(anchorId, receiverId)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

}
