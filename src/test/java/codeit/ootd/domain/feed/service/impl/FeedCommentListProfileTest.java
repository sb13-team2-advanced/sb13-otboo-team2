package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.feed.dto.response.AuthorDto;
import codeit.ootd.domain.feed.dto.response.CommentDto;
import codeit.ootd.domain.feed.dto.response.CommentDtoCursorResponse;
import codeit.ootd.domain.feed.entity.FeedComment;
import codeit.ootd.domain.feed.mapper.FeedMapper;
import codeit.ootd.domain.feed.repository.FeedCommentRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 목록 작성자 프로필 일괄 조회 테스트")
class FeedCommentListProfileTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedCommentRepository feedCommentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeedResponseAssembler feedResponseAssembler;

    @Mock
    private ProfileRepository profileRepository;

    @Spy
    private FeedMapper feedMapper = Mappers.getMapper(FeedMapper.class);

    @InjectMocks
    private FeedCommentServiceImpl service;

    @Test
    @DisplayName("조회 순서가 달라도 작성자 ID로 사진을 연결하고 프로필은 한 번만 조회한다")
    void mapsProfilesByUserIdWithOneBulkQuery() {
        UUID feedId = UUID.randomUUID();
        UUID firstAuthorId = UUID.randomUUID();
        UUID secondAuthorId = UUID.randomUUID();
        List<UUID> authorIds = List.of(firstAuthorId, secondAuthorId);

        User firstAuthor = createUser(firstAuthorId, "첫 작성자");
        User secondAuthor = createUser(secondAuthorId, "두 번째 작성자");

        Profile firstProfile = new Profile(firstAuthorId);
        firstProfile.changeProfileImageUrl("https://example.com/first.png");

        Profile secondProfile = new Profile(secondAuthorId);
        secondProfile.changeProfileImageUrl("https://example.com/second.png");

        List<FeedComment> comments = List.of(
                new FeedComment(feedId, firstAuthorId, "첫 댓글"),
                new FeedComment(feedId, secondAuthorId, "두 번째 댓글"),
                new FeedComment(feedId, firstAuthorId, "같은 작성자의 다른 댓글")
        );

        when(feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(comments);
        when(feedCommentRepository.countByFeedId(feedId)).thenReturn(3L);

        // DB에서 반환되는 순서가 댓글의 작성자 순서와 달라도 연결돼야 한다.
        when(userRepository.findAllById(authorIds))
                .thenReturn(List.of(secondAuthor, firstAuthor));
        when(profileRepository.findByUserIdIn(authorIds))
                .thenReturn(List.of(secondProfile, firstProfile));

        CommentDtoCursorResponse result = service.list(feedId, null, null, 10);

        AuthorDto expectedFirst = new AuthorDto(
                firstAuthorId, "첫 작성자", "https://example.com/first.png"
        );
        AuthorDto expectedSecond = new AuthorDto(
                secondAuthorId, "두 번째 작성자", "https://example.com/second.png"
        );

        assertThat(result.data())
                .extracting(CommentDto::author)
                .containsExactly(expectedFirst, expectedSecond, expectedFirst);
        assertThat(result.data())
                .extracting(CommentDto::content)
                .containsExactly("첫 댓글", "두 번째 댓글", "같은 작성자의 다른 댓글");

        verify(userRepository).findAllById(authorIds);
        verify(profileRepository).findByUserIdIn(authorIds);
        verifyNoMoreInteractions(userRepository, profileRepository);
        verifyNoInteractions(feedResponseAssembler);
    }

    @ParameterizedTest(name = "프로필 존재 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("프로필이 없거나 사진이 없으면 작성자 정보와 null 사진을 반환한다")
    void returnsNullImageWhenProfileOrImageIsMissing(boolean profileExists) {
        UUID feedId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        List<UUID> authorIds = List.of(authorId);
        User author = createUser(authorId, "사진 없는 작성자");

        when(feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(List.of(new FeedComment(feedId, authorId, "댓글")));
        when(feedCommentRepository.countByFeedId(feedId)).thenReturn(1L);
        when(userRepository.findAllById(authorIds)).thenReturn(List.of(author));
        when(profileRepository.findByUserIdIn(authorIds))
                .thenReturn(profileExists
                        ? List.of(new Profile(authorId))
                        : List.of());

        CommentDtoCursorResponse result = service.list(feedId, null, null, 10);

        assertThat(result.data())
                .extracting(CommentDto::author)
                .containsExactly(new AuthorDto(authorId, "사진 없는 작성자", null));

        verify(profileRepository).findByUserIdIn(authorIds);
        verifyNoMoreInteractions(profileRepository);
        verifyNoInteractions(feedResponseAssembler);
    }

    @Test
    @DisplayName("댓글이 없으면 작성자와 프로필을 조회하지 않는다")
    void skipsAuthorAndProfileQueriesForEmptyComments() {
        UUID feedId = UUID.randomUUID();

        when(feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
                feedId, PageRequest.of(0, 11)
        )).thenReturn(List.of());
        when(feedCommentRepository.countByFeedId(feedId)).thenReturn(0L);

        CommentDtoCursorResponse result = service.list(feedId, null, null, 10);

        assertThat(result.data()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.hasNext()).isFalse();

        verifyNoInteractions(
                userRepository,
                profileRepository,
                feedResponseAssembler,
                feedMapper
        );
    }

    private User createUser(UUID id, String name) {
        User user = User.create(name, id + "@feed.test", "encoded-password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}