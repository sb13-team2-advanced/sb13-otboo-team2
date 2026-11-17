package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.entity.FeedClothes;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedResponseAssembler;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:feed-create-integration;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 등록 통합 테스트")
class FeedServiceImplIntegrationTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeatherRepository weatherRepository;

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

    private User author;
    private Weather weather;
    private Clothes firstClothes;
    private Clothes secondClothes;

    @BeforeEach
    void setUp() {
        author = userRepository.saveAndFlush(
                User.create(
                        "피드 테스트 작성자",
                        UUID.randomUUID() + "@test.com",
                        "encoded-password"
                )
        );

        weather = weatherRepository.saveAndFlush(
                new Weather(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 10, 30),
                        Instant.parse("2026-10-30T00:00:00Z"),
                        18.5,
                        12.0,
                        22.0,
                        (short) 65,
                        500,
                        0.6,
                        2.4,
                        0.0,
                        3.0,
                        (short) 8
                )
        );

        firstClothes = clothesRepository.saveAndFlush(
                new Clothes(
                        author.getId(),
                        "셔츠",
                        ClothesType.TOP,
                        null
                )
        );

        secondClothes = clothesRepository.saveAndFlush(
                new Clothes(
                        author.getId(),
                        "바지",
                        ClothesType.BOTTOM,
                        null
                )
        );
    }

    @AfterEach
    void cleanUp() {
        // 이 클래스 전용 H2 테스트 DB의 데이터를 정리한다.
        feedClothesRepository.deleteAll();
        feedRepository.deleteAll();
        clothesRepository.deleteAll();
        weatherRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("피드와 의상 연결을 저장하고 전날 기온이 없는 응답을 반환한다")
    void savesFeedAndClothes() {
        FeedDto result = feedService.create(createRequest(), author.getId());

        Feed savedFeed = feedRepository.findById(result.id())
                .orElseThrow();

        List<FeedClothes> savedLinks =
                feedClothesRepository.findAll();

        assertThat(savedFeed.getAuthorId()).isEqualTo(author.getId());
        assertThat(savedFeed.getWeatherId()).isEqualTo(weather.getId());
        assertThat(savedFeed.getContent()).isEqualTo("오늘의 코디");
        assertThat(savedFeed.getWeatherTemperatureCurrent())
                .isEqualTo(18.5);
        assertThat(savedFeed.getWeatherTemperatureComparedToDayBefore())
                .isNull();
        assertThat(savedFeed.getWeatherPrecipitationType())
                .isEqualTo("RAIN");
        assertThat(savedFeed.getWeatherPrecipitationProbability())
                .isEqualTo(60.0);

        assertThat(savedLinks)
                .extracting(
                        FeedClothes::getFeedId,
                        FeedClothes::getClothesId,
                        FeedClothes::getDisplayOrder
                )
                .containsExactlyInAnyOrder(
                        tuple(result.id(), firstClothes.getId(), 0),
                        tuple(result.id(), secondClothes.getId(), 1)
                );

        assertThat(result.id()).isNotNull();
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        assertThat(result.author().userId()).isEqualTo(author.getId());
        assertThat(result.weather().weatherId()).isEqualTo(weather.getId());
        assertThat(result.weather().temperature().comparedToDayBefore())
                .isNull();
        assertThat(result.ootds())
                .extracting(ootd -> ootd.clothesId())
                .containsExactly(
                        firstClothes.getId(),
                        secondClothes.getId()
                );
        assertThat(result.likeCount()).isZero();
        assertThat(result.commentCount()).isZero();
        assertThat(result.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("저장 후 응답 구성에 실패하면 피드와 의상 연결이 모두 롤백된다")
    void rollsBackWhenResponseAssemblyFails() {
        doAnswer(invocation -> {
            // INSERT를 실제 DB에 전달한 뒤 오류를 발생시킨다.
            entityManager.flush();

            assertThat(feedRepository.count()).isEqualTo(1);
            assertThat(feedClothesRepository.count()).isEqualTo(2);

            throw new IllegalStateException("응답 구성 실패 테스트");
        }).when(feedResponseAssembler).toAuthorDto(any(User.class));

        assertThatThrownBy(() -> feedService.create(createRequest(), author.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("응답 구성 실패 테스트");

        // 서비스 트랜잭션이 종료된 후 DB를 다시 조회한다.
        assertThat(feedRepository.count()).isZero();
        assertThat(feedClothesRepository.count()).isZero();

        // 등록 전부터 존재하던 데이터는 유지된다.
        assertThat(userRepository.existsById(author.getId())).isTrue();
        assertThat(weatherRepository.existsById(weather.getId())).isTrue();
        assertThat(clothesRepository.existsById(firstClothes.getId())).isTrue();
        assertThat(clothesRepository.existsById(secondClothes.getId())).isTrue();
    }

    private FeedCreateRequest createRequest() {
        return new FeedCreateRequest(
                author.getId(),
                weather.getId(),
                List.of(firstClothes.getId(), secondClothes.getId()),
                "오늘의 코디"
        );
    }
}