package codeit.ootd.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.dto.OutfitColorMatchResult;
import codeit.ootd.domain.recommendation.dto.RecommendationExplanation;
import codeit.ootd.domain.recommendation.service.impl.RecommendationServiceImpl;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 없는 날씨와 빈 옷장 처리 및 사용자별 조회를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock private WeatherRepository weatherRepository;
    @Mock private WeatherForecastRepository forecastRepository;
    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesAttributeRepository attributeRepository;
    @Mock private ClothesAttributeDefinitionRepository definitionRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private OutfitRecommendationService recommender;
    @Mock private RecommendationExplanationService explanationService;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-11-18T06:00:00Z"), ZoneOffset.UTC);
    private RecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        RecommendationContextLoader loader = new RecommendationContextLoader(weatherRepository,
            forecastRepository, clothesRepository, attributeRepository, definitionRepository,
            profileRepository, clock);
        service = new RecommendationServiceImpl(loader, recommender, explanationService);
        lenient().when(recommender.recommendWithEvidence(any(), anyList()))
            .thenReturn(OutfitColorMatchResult.withoutEvidence(List.of()));
        lenient().when(explanationService.explain(any(), any()))
            .thenReturn(RecommendationExplanation.of("기본 설명", List.of()));
    }

    /**
     * 한국 시간의 정시·30분 경계와 날짜 시작을 기준으로 조회 시작점이 반올림되는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({
            "2026-11-18T06:00:00Z, 2026-11-18T06:00:00Z",
            "2026-11-18T06:29:59.999Z, 2026-11-18T06:00:00Z",
            "2026-11-18T06:30:00Z, 2026-11-18T07:00:00Z",
            "2026-11-18T06:59:59Z, 2026-11-18T07:00:00Z",
            "2026-11-17T15:29:59Z, 2026-11-17T15:00:00Z",
            "2026-11-17T15:30:00Z, 2026-11-17T16:00:00Z"
    })
    void 오늘은_요청_시각을_한_시간_단위로_반올림해_예보를_조회한다(String requestedAt,
            String expectedStart) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        given(clock.instant()).willReturn(Instant.parse(requestedAt));
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        verify(forecastRepository).findForecastsForRecommendation(locationId,
                Instant.parse(expectedStart), Instant.parse("2026-11-18T15:00:00Z"));
        verify(clock, times(1)).instant();
        verifyNoInteractions(profileRepository, recommender);
        assertThat(response.clothes()).isEmpty();
    }

    @Test
    void 오늘은_지난_강수와_일별_집계값을_추천에_반영하지_않는다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));
        given(forecastRepository.findForecastsForRecommendation(locationId,
                Instant.parse("2026-11-18T06:00:00Z"), Instant.parse("2026-11-18T15:00:00Z")))
                .willReturn(List.of(
                        forecast(locationId, "2026-11-18T06:00:00Z", 24, 1.0, 10, 5, 1),
                        forecast(locationId, "2026-11-18T09:00:00Z", 26, 0.0, 0, 0, 1),
                        forecast(locationId, "2026-11-18T12:00:00Z", 28, 0.0, 0, 0, 1),
                        forecast(locationId, "2026-11-18T15:00:00Z", 26, 0.0, 0, 0, 1)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(eq(RecommendationConditions.of(26, 0, 0, 0, 1)), anyList());
    }

    /**
     * 남은 예보로 기온·풍속·강수 조건을 만든 뒤 사용자 민감도까지 함께 적용하는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({"0.7, 0, 0", "0, 1, 0", "0, 0, 1"})
    void 오늘의_남은_기온과_강수와_풍속에_민감도를_반영한다(double probability,
            double rain, double snow) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        Profile profile = new Profile(userId);
        profile.update(null, null, 1);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        given(forecastRepository.findForecastsForRecommendation(locationId,
                Instant.parse("2026-11-18T06:00:00Z"), Instant.parse("2026-11-18T15:00:00Z")))
                .willReturn(List.of(
                        forecast(locationId, "2026-11-18T09:00:00Z", 12, null, 0, 0, 2),
                        forecast(locationId, "2026-11-18T12:00:00Z", 16, probability, rain, snow, 6)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(argThat(conditions ->
                conditions.temperature() == 8 && conditions.precipitationExpected()
                        && conditions.windy()), anyList());
    }

    @Test
    void 자정_예보로_오늘_마지막_강수_구간을_포함한다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        given(clock.instant()).willReturn(Instant.parse("2026-11-18T14:29:59Z"));
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));
        given(forecastRepository.findForecastsForRecommendation(locationId,
                Instant.parse("2026-11-18T14:00:00Z"), Instant.parse("2026-11-18T15:00:00Z")))
                .willReturn(List.of(forecast(locationId, "2026-11-18T15:00:00Z", 25, null, 1, 0, 1)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(eq(RecommendationConditions.of(25, 0, 1, 0, 1)), anyList());
    }

    @Test
    void 강수확률이_모두_없고_비눈_예보량도_없으면_강수_대비를_추가하지_않는다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));
        given(forecastRepository.findForecastsForRecommendation(locationId,
                Instant.parse("2026-11-18T06:00:00Z"), Instant.parse("2026-11-18T15:00:00Z")))
                .willReturn(List.of(forecast(locationId, "2026-11-18T09:00:00Z", 25, null, 0, 0, 1)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(eq(RecommendationConditions.of(25, 0, 0, 0, 1)), anyList());
    }

    @Test
    void 시작_시각에_끝난_구간만_있으면_빈_추천을_반환한다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(locationId)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));
        given(forecastRepository.findForecastsForRecommendation(locationId,
                Instant.parse("2026-11-18T06:00:00Z"), Instant.parse("2026-11-18T15:00:00Z")))
                .willReturn(List.of(forecast(locationId, "2026-11-18T06:00:00Z", 25, 1.0, 1, 0, 1)));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.clothes()).isEmpty();
        verifyNoInteractions(profileRepository, recommender);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-18T14:30:00Z", "2026-11-18T14:59:59Z"})
    void 반올림한_시각이_다음_날이면_오늘의_추천은_빈_목록이다(String requestedAt) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        given(clock.instant()).willReturn(Instant.parse(requestedAt));
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(todayWeather(UUID.randomUUID())));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.clothes()).isEmpty();
        verifyNoInteractions(forecastRepository, profileRepository, recommender);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-17", "2026-11-19"})
    void 오늘이_아닌_날짜는_해당_날짜_전체의_날씨로_추천한다(String weatherDate) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        Weather weather = new Weather(UUID.randomUUID(), LocalDate.parse(weatherDate), clock.instant(),
                25, 25, 25, (short) 50, 800, 0.7, 1, 0, 1, (short) 8);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(mock(Clothes.class)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(eq(RecommendationConditions.of(25, 0.7, 1, 0, 1)), anyList());
        verifyNoInteractions(forecastRepository);
    }

    /**
     * 지난 강수와 낮은 기온이 들어 있는 일별 집계로 오늘 예보가 이를 대체하는지 검증한다.
     */
    private static Weather todayWeather(UUID locationId) {
        return new Weather(locationId, LocalDate.of(2026, 11, 18), Instant.parse("2026-11-18T03:00:00Z"),
                5, 0, 25, (short) 50, 500, 1.0, 10, 5, 8, (short) 8);
    }

    /**
     * 지정한 시각의 예보와 직전 구간의 강수량을 가진 테스트 자료를 만든다.
     */
    private static WeatherForecast forecast(UUID locationId, String forecastAt, double temperature,
            Double probability, double rain, double snow, double wind) {
        return new WeatherForecast(locationId, Instant.parse(forecastAt),
                Instant.parse("2026-11-18T03:00:00Z"), temperature, (short) 50, 800,
                probability, rain, snow, wind);
    }

    /**
     * 의상 속성이 참조하는 정의가 누락되면 추천을 중단하는지 검증한다.
     */
    @Test
    void 의상_속성_정의가_없으면_예외를_발생시킨다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Clothes clothes = mock(Clothes.class);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(mock(Weather.class)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(clothes));
        given(clothes.getId()).willReturn(clothesId);
        given(attributeRepository.findAllByClothesIdIn(List.of(clothesId)))
                .willReturn(List.of(new ClothesAttribute(clothesId, definitionId, "보통")));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of());

        // when then
        assertThatThrownBy(() -> service.recommend(userId, weatherId))
                .isInstanceOfSatisfying(OtbooException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND));
        verifyNoInteractions(recommender);
    }

    @Test
    void 프로필이_없으면_의상과_속성을_조합해_평균_기온으로_추천한다() {

        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Weather weather = mock(Weather.class);
        Clothes clothes = mock(Clothes.class);
        ClothesAttributeDefinition definition = mock(ClothesAttributeDefinition.class);

        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureAvg()).willReturn(15.0);
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(clothes));
        given(clothes.getId()).willReturn(clothesId);
        given(clothes.getName()).willReturn("니트");
        given(clothes.getType()).willReturn(ClothesType.TOP);
        given(attributeRepository.findAllByClothesIdIn(List.of(clothesId))).willReturn(List.of(
                new ClothesAttribute(clothesId, definitionId, "보통")));
        given(definitionRepository.findAllById(List.of(definitionId))).willReturn(List.of(definition));
        given(definition.getId()).willReturn(definitionId);
        given(definition.getName()).willReturn("두께");
        given(definition.getSelectableValues()).willReturn(List.of("얇음", "보통", "두꺼움"));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        RecommendationClothesResponse expected = new RecommendationClothesResponse(
                clothesId, "니트", null, ClothesType.TOP,
                List.of(new ClothesAttributeWithDefDto(
                        definitionId, "두께", List.of("얇음", "보통", "두꺼움"), "보통")));

        given(recommender.recommendWithEvidence(RecommendationConditions.of(15.0, 0, 0, 0, 0), List.of(expected))).willReturn(OutfitColorMatchResult.withoutEvidence(List.of(expected)));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.clothes()).containsExactly(expected);
        verify(clothesRepository).findAllByUserId(userId);
        verify(profileRepository).findByUserId(userId);
        verify(recommender).recommendWithEvidence(RecommendationConditions.of(15.0, 0, 0, 0, 0), List.of(expected));
    }

    @Test
    void 없는_날씨는_추천_실패를_반환한다() {

        // given
        UUID weatherId = UUID.randomUUID();
        given(weatherRepository.findById(weatherId)).willReturn(Optional.empty());

        // when then
        assertThatThrownBy(() -> service.recommend(UUID.randomUUID(), weatherId))
                .isInstanceOfSatisfying(OtbooException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_WEATHER_NOT_FOUND));
        verifyNoInteractions(clothesRepository, profileRepository, recommender);
    }

    @Test
    void 본인의_옷장이_비면_속성_조회없이_빈_추천을_반환한다() {

        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(mock(Weather.class)));
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of());

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.weatherId()).isEqualTo(weatherId);
        assertThat(response.clothes()).isEmpty();
        verify(clothesRepository).findAllByUserId(userId);
        verifyNoInteractions(attributeRepository, definitionRepository, profileRepository, recommender);
    }

    @ParameterizedTest
    @CsvSource({"1, 11.0", "2, 13.0", "3, 15.0", "4, 17.0", "5, 19.0"})
    void 프로필_민감도로_보정한_기온을_추천기에_전달한다(int sensitivity, double expectedTemperature) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        Weather weather = mock(Weather.class);
        Clothes clothes = mock(Clothes.class);
        UUID clothesId = UUID.randomUUID();
        Profile profile = new Profile(userId);
        profile.update(null, null, sensitivity);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureAvg()).willReturn(15.0);
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(clothes));
        given(clothes.getId()).willReturn(clothesId);
        given(clothes.getName()).willReturn("상의");
        given(clothes.getType()).willReturn(ClothesType.TOP);
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
        RecommendationClothesResponse candidate = new RecommendationClothesResponse(
                clothesId, "상의", null, ClothesType.TOP, List.of());
        given(recommender.recommendWithEvidence(RecommendationConditions.of(expectedTemperature, 0, 0, 0, 0), List.of(candidate))).willReturn(OutfitColorMatchResult.withoutEvidence(List.of(candidate)));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.clothes()).containsExactly(candidate);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.weatherId()).isEqualTo(weatherId);
        verify(profileRepository).findByUserId(userId);
        verify(recommender).recommendWithEvidence(RecommendationConditions.of(expectedTemperature, 0, 0, 0, 0), List.of(candidate));
    }

    @Test
    void 프로필의_민감도가_없으면_평균_기온으로_추천한다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        Weather weather = mock(Weather.class);
        Clothes clothes = mock(Clothes.class);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureAvg()).willReturn(15.0);
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(clothes));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(new Profile(userId)));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(eq(RecommendationConditions.of(15.0, 0, 0, 0, 0)), anyList());
    }

    /**
     * 사용자 민감도로 보정한 기온과 저장된 강수·풍속을 함께 추천 조건으로 전달하는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({"1, 8.0", "3, 12.0", "5, 16.0"})
    void 사용자_민감도와_날씨를_함께_추천에_반영한다(int sensitivity, double expectedTemperature) {
        // given
        UUID userId = UUID.randomUUID();
        UUID weatherId = UUID.randomUUID();
        Weather weather = mock(Weather.class);
        Clothes clothes = mock(Clothes.class);
        Profile profile = new Profile(userId);
        profile.update(null, null, sensitivity);
        given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
        given(weather.getTemperatureAvg()).willReturn(14.0);
        given(weather.getPrecipitationProbability()).willReturn(0.7);
        given(weather.getRainAmount()).willReturn(1.0);
        given(weather.getSnowAmount()).willReturn(0.5);
        given(weather.getWindSpeed()).willReturn(4.0);
        given(clothesRepository.findAllByUserId(userId)).willReturn(List.of(clothes));
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

        // when
        service.recommend(userId, weatherId);

        // then
        verify(recommender).recommendWithEvidence(argThat(conditions ->
                conditions.temperature() == expectedTemperature
                        && conditions.precipitationExpected() && conditions.windy()), anyList());
        verify(weather).getRainAmount();
        verify(weather).getSnowAmount();
    }
}
