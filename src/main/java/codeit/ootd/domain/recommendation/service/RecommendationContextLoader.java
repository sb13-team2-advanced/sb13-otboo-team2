package codeit.ootd.domain.recommendation.service;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.recommendation.algorithm.RecommendationConditions;
import codeit.ootd.domain.recommendation.dto.RecommendationContext;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationContextLoader {

    private static final int DEFAULT_TEMPERATURE_SENSITIVITY = 3;
    private static final double TEMPERATURE_ADJUSTMENT_PER_LEVEL = 2.0;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int HALF_HOUR_MINUTES = 30;

    private final WeatherRepository weatherRepository;
    private final WeatherForecastRepository forecastRepository;
    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository attributeRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final ProfileRepository profileRepository;
    private final Clock clock;

    /**
     * 오늘은 요청 시각을 한 시간 단위로 반올림한 이후의 예보와 사용자 온도 민감도로 추천한다.
     * 다른 날짜는 일별 날씨를 사용하며 오늘의 남은 예보가 없으면 추천 조건을 비운다.
     * 본인 의상과 속성을 DTO로 변환하여 외부 임베딩 호출 전에 DB 조회 트랜잭션을 마친다.
     */
    public RecommendationContext load(UUID userId, UUID weatherId) {

        Instant requestedAt = clock.instant();
        Weather weather = weatherRepository.findById(weatherId)
                .orElseThrow(() -> new OtbooException(ErrorCode.RECOMMENDATION_WEATHER_NOT_FOUND));

        List<Clothes> clothes = clothesRepository.findAllByUserId(userId);

        if (clothes.isEmpty()) {
            return RecommendationContext.of(Optional.empty(), List.of());
        }

        List<RecommendationClothesResponse> candidates = createCandidates(clothes);

        return RecommendationContext.of(recommendationConditions(userId, weather, requestedAt), candidates);
    }

    /**
     * 한국 시간 기준 30분 이상은 다음 정시로 반올림하고 오늘 자정까지의 예보를 집계한다.
     * 강수는 직전 구간의 누적값이므로 시작 시각에 이미 종료된 구간은 제외한다.
     * 자정 예보는 오늘 마지막 강수 구간과 남은 시간대의 기온·풍속 평가에 포함한다.
     */
    private Optional<RecommendationConditions> recommendationConditions(UUID userId, Weather weather,
            Instant requestedAt) {

        LocalDate today = requestedAt.atZone(KOREA_ZONE).toLocalDate();
        if (!today.equals(weather.getWeatherDate())) {
            return Optional.of(RecommendationConditions.of(
                    recommendationTemperature(userId, weather.getTemperatureAvg()),
                    weather.getPrecipitationProbability(), weather.getRainAmount(),
                    weather.getSnowAmount(), weather.getWindSpeed()));
        }

        Instant start = requestedAt.atZone(KOREA_ZONE)
                .plusMinutes(HALF_HOUR_MINUTES)
                .truncatedTo(ChronoUnit.HOURS)
                .toInstant();

        Instant end = today.plusDays(1)
                .atStartOfDay(KOREA_ZONE)
                .toInstant();

        if (!start.isBefore(end)) return Optional.empty();

        List<WeatherForecast> forecasts = forecastRepository.findForecastsForRecommendation(weather.getLocationId(), start, end);

        List<WeatherForecast> precipitationForecasts = forecasts.stream()
                .filter(forecast -> forecast.getForecastAt().isAfter(start))
                .toList();

        if (precipitationForecasts.isEmpty()) return Optional.empty();

        double temperature = forecasts.stream()
                .mapToDouble(WeatherForecast::getTemperature)
                .average()
                .orElseThrow();

        double windSpeed = forecasts.stream()
                .mapToDouble(WeatherForecast::getWindSpeed)
                .average()
                .orElseThrow();

        double probability = precipitationForecasts.stream()
                .map(WeatherForecast::getPrecipitationProbability)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        double rain = precipitationForecasts.stream()
                .mapToDouble(WeatherForecast::getRainAmount)
                .sum();

        double snow = precipitationForecasts.stream()
                .mapToDouble(WeatherForecast::getSnowAmount)
                .sum();

        return Optional.of(RecommendationConditions.of(
                recommendationTemperature(userId, temperature),
                probability,
                rain,
                snow,
                windSpeed
        ));
    }

    /**
     * 민감도 1은 추위, 5는 더위에 민감하므로 3을 기준으로 단계마다 2도를 더한다.
     * 프로필이나 민감도가 없으면 기본값 3을 적용해 실제 평균 기온을 유지한다.
     */
    private double recommendationTemperature(UUID userId, double temperature) {

        int sensitivity = profileRepository.findByUserId(userId)
                .map(Profile::getTemperatureSensitivity)
                .orElse(DEFAULT_TEMPERATURE_SENSITIVITY);

        return temperature + (sensitivity - DEFAULT_TEMPERATURE_SENSITIVITY)
                * TEMPERATURE_ADJUSTMENT_PER_LEVEL;
    }

    /**
     * 의상 속성과 정의를 일괄 조회한 뒤 의상별 추천 입력으로 변환한다.
     */
    private List<RecommendationClothesResponse> createCandidates(List<Clothes> clothes) {

        List<ClothesAttribute> attributes = attributeRepository.findAllByClothesIdIn(
                clothes.stream().map(Clothes::getId).toList());
        Map<UUID, ClothesAttributeDefinition> definitions = findDefinitions(attributes);
        Map<UUID, List<ClothesAttribute>> attributesByClothesId = attributes.stream()
                .collect(Collectors.groupingBy(ClothesAttribute::getClothesId));

        return clothes.stream()
                .map(item -> toRecommendationClothes(item,
                        attributesByClothesId.getOrDefault(item.getId(), List.of()), definitions))
                .toList();
    }

    /**
     * 중복을 제거한 속성 정의 ID로 조회해 정의를 ID별로 묶는다.
     */
    private Map<UUID, ClothesAttributeDefinition> findDefinitions(List<ClothesAttribute> attributes) {

        List<UUID> definitionIds = attributes.stream()
                .map(ClothesAttribute::getDefinitionId)
                .distinct()
                .toList();

        return definitionRepository.findAllById(definitionIds).stream()
                .collect(Collectors.toMap(ClothesAttributeDefinition::getId, Function.identity()));
    }

    /**
     * 의상 정보와 해당 의상의 속성 응답을 결합한다.
     */
    private RecommendationClothesResponse toRecommendationClothes(Clothes clothes,
                                                                  List<ClothesAttribute> attributes, Map<UUID, ClothesAttributeDefinition> definitions) {

        List<ClothesAttributeWithDefDto> requests = attributes.stream()
                .map(attribute -> toAttribute(attribute, definitions))
                .toList();

        return new RecommendationClothesResponse(clothes.getId(), clothes.getName(),
                clothes.getImageUrl(), clothes.getType(), requests);
    }

    /**
     * 속성 정의가 없으면 예외를 발생시키고, 있으면 정의 정보와 속성값을 결합한다.
     */
    private ClothesAttributeWithDefDto toAttribute(ClothesAttribute attribute,
                                                   Map<UUID, ClothesAttributeDefinition> definitions) {

        ClothesAttributeDefinition definition = definitions.get(attribute.getDefinitionId());

        if (definition == null) {
            throw new OtbooException(ErrorCode.CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND);
        }

        return new ClothesAttributeWithDefDto(definition.getId(), definition.getName(),
                List.copyOf(definition.getSelectableValues()), attribute.getValue());
    }
}
