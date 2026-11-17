package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.dto.response.OotdDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-update-integration;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 수정 통합 테스트")
class FeedServiceImplUpdateIntegrationTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClothesRepository clothesRepository;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private FeedClothesRepository feedClothesRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private FeedResponseAssembler feedResponseAssembler;

    private UUID authorId;
    private UUID feedId;
    private UUID clothesId;
    private UUID weatherId;

    @BeforeEach
    void setUp() {
        User author = userRepository.saveAndFlush(
                User.create(
                        "수정 테스트 작성자",
                        UUID.randomUUID() + "@feed-update.test",
                        "encoded-password"
                )
        );
        authorId = author.getId();

        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(
                        authorId,
                        "셔츠",
                        ClothesType.TOP,
                        "/images/shirt.png"
                )
        );
        clothesId = clothes.getId();
        weatherId = UUID.randomUUID();

        Feed feed = feedRepository.saveAndFlush(
                Feed.builder()
                        .authorId(authorId)
                        .weatherId(weatherId)
                        .content("기존 내용")
                        .weatherSkyStatus("CLEAR")
                        .weatherPrecipitationType("NONE")
                        .weatherTemperatureCurrent(20.0)
                        .weatherTemperatureMin(15.0)
                        .weatherTemperatureMax(25.0)
                        .build()
        );
        feedId = feed.getId();

        feedClothesRepository.saveAndFlush(
                new FeedClothes(feedId, clothesId, 0)
        );
    }

    @AfterEach
    void cleanUp() {
        // 이 클래스 전용 H2 테스트 DB의 데이터를 정리한다.
        feedClothesRepository.deleteAll();
        feedRepository.deleteAll();
        clothesRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("수정 내용을 DB에 저장하고 기존 의상과 날씨를 유지한다")
    void persistsUpdatedContent() {
        FeedDto response = feedService.update(
                feedId,
                new FeedUpdateRequest("수정한 내용"),
                authorId
        );

        // 테스트에 트랜잭션을 걸지 않아 서비스 종료 후 DB를 다시 읽는다.
        Feed saved = feedRepository.findById(feedId).orElseThrow();

        assertThat(saved.getContent()).isEqualTo("수정한 내용");
        assertThat(saved.getAuthorId()).isEqualTo(authorId);
        assertThat(saved.getWeatherId()).isEqualTo(weatherId);
        assertThat(saved.getWeatherTemperatureCurrent()).isEqualTo(20.0);
        assertThat(saved.getWeatherTemperatureComparedToDayBefore()).isNull();

        assertThat(feedRepository.count()).isEqualTo(1L);
        assertThat(feedClothesRepository.findAll())
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.getFeedId()).isEqualTo(feedId);
                    assertThat(link.getClothesId()).isEqualTo(clothesId);
                    assertThat(link.getDisplayOrder()).isZero();
                });

        assertThat(response.id()).isEqualTo(feedId);
        assertThat(response.content()).isEqualTo("수정한 내용");
        assertThat(response.author().userId()).isEqualTo(authorId);
        assertThat(response.weather().weatherId()).isEqualTo(weatherId);
        assertThat(response.ootds())
                .extracting(OotdDto::clothesId)
                .containsExactly(clothesId);
    }

    @Test
    @DisplayName("응답 구성에 실패하면 DB의 수정 내용도 롤백된다")
    void rollsBackWhenResponseAssemblyFails() {
        doAnswer(invocation -> {
            // UPDATE를 실제 DB에 전달한 뒤 실패시켜 롤백을 검증한다.
            entityManager.flush();

            String content = (String) entityManager.createNativeQuery(
                            "SELECT content FROM feeds WHERE id = :id"
                    )
                    .setParameter("id", feedId)
                    .getSingleResult();

            assertThat(content).isEqualTo("수정한 내용");

            throw new IllegalStateException("응답 구성 실패 테스트");
        }).when(feedResponseAssembler)
                .toFeedDtos(anyList(), eq(authorId));

        assertThatThrownBy(() -> feedService.update(
                feedId,
                new FeedUpdateRequest("수정한 내용"),
                authorId
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("응답 구성 실패 테스트");

        Feed saved = feedRepository.findById(feedId).orElseThrow();

        assertThat(saved.getContent()).isEqualTo("기존 내용");
        assertThat(feedClothesRepository.count()).isEqualTo(1L);
    }
}