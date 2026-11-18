package codeit.ootd.domain.recommendation.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

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
            "12, 7.99, 10, true", "12, 8, 8, true", "20, 8, 16, true",
            "20.1, 8, 20.1, true", "30, 8, 30, true"
    })
    void 서늘할_때만_풍속에_따라_추천_기온을_낮춘다(double temperature, double windSpeed,
            double expectedTemperature, boolean windy) {
        // given
        double probability = 0;

        // when
        RecommendationConditions conditions = RecommendationConditions.of(temperature, probability,
                0, 0, windSpeed);

        // then
        assertThat(conditions.temperature()).isEqualTo(expectedTemperature);
        assertThat(conditions.windy()).isEqualTo(windy);
    }
}
