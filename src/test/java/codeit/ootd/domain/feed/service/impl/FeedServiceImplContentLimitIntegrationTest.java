package codeit.ootd.domain.feed.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.feed.dto.request.FeedCreateRequest;
import codeit.ootd.domain.feed.dto.request.FeedUpdateRequest;
import codeit.ootd.domain.feed.dto.response.FeedDto;
import codeit.ootd.domain.feed.entity.Feed;
import codeit.ootd.domain.feed.repository.FeedClothesRepository;
import codeit.ootd.domain.feed.repository.FeedRepository;
import codeit.ootd.domain.feed.service.FeedService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
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

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feed-content-limit;DB_CLOSE_DELAY=-1"
})
@DisplayName("피드 내용 최대 길이 통합 테스트")
class FeedServiceImplContentLimitIntegrationTest {

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

    private UUID authorId;
    private UUID weatherId;
    private UUID clothesId;

    @BeforeEach
    void setUp() {
        User author = userRepository.saveAndFlush(
                User.create(
                        "길이 검증 작성자",
                        UUID.randomUUID() + "@content-limit.test",
                        "encoded-password"
                )
        );
        authorId = author.getId();

        Weather weather = weatherRepository.saveAndFlush(
                new Weather(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 11, 6),
                        Instant.parse("2026-11-06T00:00:00Z"),
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
        weatherId = weather.getId();

        Clothes clothes = clothesRepository.saveAndFlush(
                new Clothes(
                        authorId,
                        "셔츠",
                        ClothesType.TOP,
                        null
                )
        );
        clothesId = clothes.getId();
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
    @DisplayName("정확히 1000자인 내용을 등록하고 DB와 응답에 그대로 유지한다")
    void createsContentAtLimit() {
        String content = "가".repeat(1000);

        FeedDto response = feedService.create(
                createRequest(content),
                authorId
        );

        Feed saved = feedRepository.findById(response.id())
                .orElseThrow();

        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getContent()).hasSize(1000);
        assertThat(response.content()).isEqualTo(content);
        assertThat(feedRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("정확히 1000자인 내용으로 수정하고 DB와 응답에 그대로 유지한다")
    void updatesContentAtLimit() {
        FeedDto created = feedService.create(
                createRequest("기존 내용"),
                authorId
        );
        String content = "나".repeat(1000);

        FeedDto response = feedService.update(
                created.id(),
                new FeedUpdateRequest(content),
                authorId
        );

        Feed saved = feedRepository.findById(created.id())
                .orElseThrow();

        assertThat(saved.getContent()).isEqualTo(content);
        assertThat(saved.getContent()).hasSize(1000);
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.id()).isEqualTo(created.id());
        assertThat(feedRepository.count()).isEqualTo(1L);
    }

    private FeedCreateRequest createRequest(String content) {
        return new FeedCreateRequest(
                authorId,
                weatherId,
                List.of(clothesId),
                content
        );
    }
}