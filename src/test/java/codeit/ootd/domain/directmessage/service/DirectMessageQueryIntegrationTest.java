package codeit.ootd.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.directmessage.dto.DirectMessageDtoCursorResponse;
import codeit.ootd.domain.directmessage.entity.DirectMessage;
import codeit.ootd.domain.directmessage.repository.DirectMessageQueryRepository;
import codeit.ootd.domain.directmessage.repository.DirectMessageRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.config.JpaAuditingConfig;
import codeit.ootd.global.config.QuerydslConfig;
import codeit.ootd.global.enums.SortDirection;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({DirectMessageService.class, DirectMessageQueryRepository.class,
        JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DisplayName("DM 목록 조회 통합 테스트")
class DirectMessageQueryIntegrationTest {

    @Autowired private DirectMessageService service;
    @Autowired private DirectMessageRepository messages;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private EntityManager entityManager;

    private UUID mine;
    private UUID other;
    private UUID third;
    private static final Instant TIME = Instant.parse("2026-11-16T00:00:00Z");

    @BeforeEach
    void setUp() {
        mine = createUser("mine");
        other = createUser("other");
        third = createUser("third");
        Profile profile = new Profile(other);
        profile.changeProfileImageUrl("/other.png");
        profiles.saveAndFlush(profile);
    }

    @Test
    @DisplayName("양방향 대화만 최신순으로 누락과 중복 없이 페이지 조회한다")
    void paginatesBothDirectionsWithoutGapsOrDuplicatesAndExcludesOtherConversations() {
        save(mine, other, TIME.plusSeconds(1));
        for (int i = 0; i < 5; i++) {
            save(i % 2 == 0 ? mine : other, i % 2 == 0 ? other : mine, TIME);
        }
        save(other, mine, TIME.minusSeconds(1));
        save(mine, third, TIME);
        save(third, mine, TIME);
        save(other, third, TIME);
        entityManager.clear();

        List<UUID> expected = entityManager.createQuery("""
                select m.id from DirectMessage m
                where (m.senderId = :mine and m.receiverId = :other)
                   or (m.senderId = :other and m.receiverId = :mine)
                order by m.createdAt desc, m.id desc
                """, UUID.class).setParameter("mine", mine).setParameter("other", other).getResultList();
        List<UUID> actual = new ArrayList<>();
        DirectMessageDtoCursorResponse page = service.getMessages(mine, other, null, null, 2);
        int pageCount = 0;
        while (true) {
            assertThat(++pageCount).isLessThanOrEqualTo(4);
            assertThat(page.totalCount()).isEqualTo(7);
            assertThat(page.sortBy()).isEqualTo("createdAt");
            assertThat(page.sortDirection()).isEqualTo(SortDirection.DESCENDING);
            for (DirectMessageDto message : page.data()) {
                actual.add(message.id());
                assertThat(message.sender().userId()).isIn(mine, other);
                assertThat(message.receiver().userId()).isIn(mine, other);
                var otherSummary = message.sender().userId().equals(other)
                        ? message.sender() : message.receiver();
                assertThat(otherSummary.name()).isEqualTo("other");
                assertThat(otherSummary.profileImageUrl()).isEqualTo("/other.png");
            }
            if (!page.hasNext()) break;
            DirectMessageDtoCursorResponse next = service.getMessages(
                    mine, other, page.nextCursor(), page.nextIdAfter(), 2);
            assertThat(service.getMessages(mine, other, null, page.nextIdAfter(), 2)).isEqualTo(next);
            page = next;
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.nextIdAfter()).isNull();
        assertThat(service.getMessages(other, mine, null, null, 30).data())
                .extracting(DirectMessageDto::id).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("대화가 없으면 다음 커서 없이 빈 목록을 반환한다")
    void returnsEmptyConversation() {
        var page = service.getMessages(mine, other, null, null, 30);
        assertThat(page.data()).isEmpty();
        assertThat(page.totalCount()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("다른 대화의 커서와 누락되거나 유효하지 않은 커서를 거부한다")
    void rejectsForeignMissingMismatchedAndIncompleteCursors() {
        UUID anchor = save(mine, other, TIME);
        UUID foreign = save(other, third, TIME);
        entityManager.clear();
        assertInvalidCursor(TIME.toString(), foreign);
        assertInvalidCursor(null, foreign);
        assertInvalidCursor(TIME.toString(), UUID.randomUUID());
        assertInvalidCursor(TIME.plusSeconds(1).toString(), anchor);
        assertInvalidCursor(TIME.toString(), null);
        assertInvalidCursor("invalid-time", anchor);
    }

    @Test
    @DisplayName("미인증 사용자와 존재하지 않는 상대방의 조회를 거부한다")
    void requiresAuthenticationAndExistingCounterpart() {
        assertThatThrownBy(() -> service.getMessages(null, other, null, null, 20))
                .isInstanceOfSatisfying(OtbooException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
        assertThatThrownBy(() -> service.getMessages(mine, UUID.randomUUID(), null, null, 20))
                .isInstanceOfSatisfying(OtbooException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DIRECT_MESSAGE_USER_NOT_FOUND));
    }

    @ParameterizedTest
    @DisplayName("조회 개수가 허용 범위인 1~30을 벗어나면 거부한다")
    @ValueSource(ints = {0, 31})
    void rejectsOutOfRangeLimit(int limit) {
        assertThatThrownBy(() -> service.getMessages(mine, other, null, null, limit))
                .isInstanceOfSatisfying(OtbooException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private void assertInvalidCursor(String cursor, UUID idAfter) {
        assertThatThrownBy(() -> service.getMessages(mine, other, cursor, idAfter, 2))
                .isInstanceOfSatisfying(OtbooException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.DIRECT_MESSAGE_INVALID_CURSOR));
    }

    private UUID createUser(String name) {
        return users.saveAndFlush(User.create(name, UUID.randomUUID() + "@example.com", "encoded")).getId();
    }

    private UUID save(UUID sender, UUID receiver, Instant time) {
        DirectMessage message = messages.saveAndFlush(new DirectMessage(sender, receiver, "hello"));
        entityManager.createNativeQuery("update direct_messages set created_at = :time where id = :id")
                .setParameter("time", time).setParameter("id", message.getId()).executeUpdate();
        return message.getId();
    }
}
