package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 강수 판정과 바람에 따른 추천 기온의 경계값을 검증한다.
 */
class RecommendationConditionsTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, false", "0.59, 0, 0, false", "0.6, 0, 0, true",
            "1, 0, 0, true", "0, 0.1, 0, true", "0, 0, 0.1, true"
    })
    void 강수확률이나_비눈_예보량으로_강수를_판정한다(double probability, double rain,
            double snow, boolean expected) {
        // given
        double temperature = 15;

        // when
        RecommendationConditions conditions = RecommendationConditions.of(temperature, probability,
                rain, snow, 0);

        // then
        assertThat(conditions.precipitationExpected()).isEqualTo(expected);
        assertThat(conditions.temperature()).isEqualTo(temperature);
    }

    @ParameterizedTest
    @CsvSource({
            "12, 0, 12, false", "12, 3.99, 12, false", "12, 4, 10, true",
            "12, 7.99, 10, true", "12, 8, 8, true", "19.9, 8, 15.9, true",
            "20, 4, 18, true", "20, 8, 16, true",
            "20.1, 4, 18.166666667, true", "20.1, 8, 16.233333333, true",
            "20.49, 8, 17.143333333, true", "20.5, 8, 17.166666667, true",
            "21.5, 0, 21.5, false", "21.5, 3.99, 21.5, false",
            "21.5, 4, 20.5, true", "21.5, 8, 19.5, true",
            "22, 4, 21.333333333, true", "22, 8, 20.666666667, true",
            "22.9, 8, 22.766666667, true", "23, 4, 23, true", "23, 8, 23, true",
            "23.1, 8, 23.1, true", "30, 8, 30, true"
    })
    void 기온이_높아질수록_풍속_보정량을_점진적으로_줄인다(double temperature, double windSpeed,
            double expectedTemperature, boolean windy) {
        // given
        double probability = 0;

        // when
        RecommendationConditions conditions = RecommendationConditions.of(temperature, probability,
                0, 0, windSpeed);

        // then
        assertThat(conditions.temperature()).isCloseTo(expectedTemperature, offset(0.000001));
        assertThat(conditions.windy()).isEqualTo(windy);
    }

    /**
     * 보정 구간 양 끝에서 미세한 기온 변화가 추천 기온의 큰 변화로 이어지지 않는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({"20, 4", "20, 8", "23, 4", "23, 8"})
    void 보정_시작과_종료_경계에서_추천_기온이_연속적으로_변한다(double boundary, double windSpeed) {
        // given
        double temperatureDifference = 0.000001;

        // when
        RecommendationConditions below = RecommendationConditions.of(
                boundary - temperatureDifference, 0, 0, 0, windSpeed);
        RecommendationConditions at = RecommendationConditions.of(boundary, 0, 0, 0, windSpeed);
        RecommendationConditions above = RecommendationConditions.of(
                boundary + temperatureDifference, 0, 0, 0, windSpeed);

        // then
        assertThat(above.temperature() - below.temperature()).isBetween(0.0, 0.001);
        assertThat(at.temperature()).isBetween(below.temperature(), above.temperature());
    }
}
