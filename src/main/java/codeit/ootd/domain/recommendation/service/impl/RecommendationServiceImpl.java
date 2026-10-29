package codeit.ootd.domain.recommendation.service.impl;

import codeit.ootd.domain.clothes.dto.response.ClothesAttributeWithDefDto;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.recommendation.algorithm.OutfitRecommender;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.RecommendationService;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationServiceImpl implements RecommendationService {

    private final WeatherRepository weatherRepository;
    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository attributeRepository;
    private final ClothesAttributeDefinitionRepository definitionRepository;
    private final OutfitRecommender recommender;

    /**
     * 사용자 소유 의상과 속성을 일괄 조회하고 해당 날씨의 평균 기온으로 추천한다.
     */
    @Transactional(readOnly = true)
    @Override
    public RecommendationResponse recommend(UUID userId, UUID weatherId) {

        Weather weather = weatherRepository.findById(weatherId)
                .orElseThrow(() -> new OtbooException(ErrorCode.RECOMMENDATION_WEATHER_NOT_FOUND));

        List<Clothes> clothes = clothesRepository.findAllByUserId(userId);

        if (clothes.isEmpty()) {
            return new RecommendationResponse(weatherId, userId, List.of());
        }

        List<RecommendationClothesResponse> candidates = createCandidates(clothes);
        return new RecommendationResponse(weatherId, userId,
                recommender.recommend(weather.getTemperatureAvg(), candidates));
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
