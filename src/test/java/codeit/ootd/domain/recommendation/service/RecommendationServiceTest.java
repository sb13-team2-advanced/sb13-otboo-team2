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
import codeit.ootd.domain.recommendation.algorithm.OutfitRecommender;
import codeit.ootd.domain.recommendation.dto.response.RecommendationClothesResponse;
import codeit.ootd.domain.recommendation.dto.response.RecommendationResponse;
import codeit.ootd.domain.recommendation.service.impl.RecommendationServiceImpl;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 없는 날씨와 빈 옷장 처리 및 사용자별 조회를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock private WeatherRepository weatherRepository;
    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesAttributeRepository attributeRepository;
    @Mock private ClothesAttributeDefinitionRepository definitionRepository;
    @Mock private OutfitRecommender recommender;
    @InjectMocks private RecommendationServiceImpl service;

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
    void 의상과_속성을_조합해_알고리즘에_전달한다() {

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

        RecommendationClothesResponse expected = new RecommendationClothesResponse(
                clothesId, "니트", null, ClothesType.TOP,
                List.of(new ClothesAttributeWithDefDto(
                        definitionId, "두께", List.of("얇음", "보통", "두꺼움"), "보통")));

        given(recommender.recommend(15.0, List.of(expected))).willReturn(List.of(expected));

        // when
        RecommendationResponse response = service.recommend(userId, weatherId);

        // then
        assertThat(response.clothes()).containsExactly(expected);
        verify(clothesRepository).findAllByUserId(userId);
        verify(recommender).recommend(15.0, List.of(expected));
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
        verifyNoInteractions(clothesRepository, recommender);
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
        verifyNoInteractions(attributeRepository, definitionRepository, recommender);
    }
}
