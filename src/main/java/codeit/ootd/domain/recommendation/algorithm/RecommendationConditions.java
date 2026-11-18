package codeit.ootd.domain.recommendation.algorithm;

/**
 * 사용자 민감도로 보정된 기온과 날씨를 추천 조건으로 변환한다.
 * 바람 보정은 의상 선택을 위한 서비스 규칙이며 기상학적 체감온도 공식은 아니다.
 */
public record RecommendationConditions(double temperature, boolean precipitationExpected,
                                       boolean windy) {

    private static final double PRECIPITATION_PROBABILITY_THRESHOLD = 0.6;
    private static final double WIND_SPEED_THRESHOLD = 4.0;
    private static final double STRONG_WIND_SPEED_THRESHOLD = 8.0;
    private static final double WIND_COOLING_FULL_TEMPERATURE = 20.0;
    private static final double WIND_COOLING_END_TEMPERATURE = 23.0;
    private static final double WIND_TEMPERATURE_ADJUSTMENT = 2.0;
    private static final double STRONG_WIND_TEMPERATURE_ADJUSTMENT = 4.0;

    /**
     * 강수확률이 60% 이상이거나 비·눈 예보량이 있으면 강수에 대비한다.
     * 보정 기온이 20도 이하일 때 풍속 4m/s 이상은 2도, 8m/s 이상은 4도를 추가로 낮춘다.
     * 20도부터 23도까지는 보정량을 선형으로 줄이고 23도 이상에서는 기온을 낮추지 않는다.
     */
    public static RecommendationConditions of(double temperature, double precipitationProbability,
            double rainAmount, double snowAmount, double windSpeed) {

        boolean precipitationExpected = precipitationProbability >= PRECIPITATION_PROBABILITY_THRESHOLD
                || rainAmount > 0 || snowAmount > 0;

        boolean windy = windSpeed >= WIND_SPEED_THRESHOLD;

        double adjustedTemperature = temperature;

        if (windy) {
            double adjustment = windSpeed >= STRONG_WIND_SPEED_THRESHOLD
                    ? STRONG_WIND_TEMPERATURE_ADJUSTMENT : WIND_TEMPERATURE_ADJUSTMENT;
            adjustedTemperature -= adjustment * windCoolingFactor(temperature);
        }

        return new RecommendationConditions(adjustedTemperature, precipitationExpected, windy);
    }

    /**
     * 보정 비율을 20도에서 1, 23도에서 0이 되도록 선형 계산하고 범위 밖은 0과 1로 제한한다.
     */
    private static double windCoolingFactor(double temperature) {
        return Math.max(0.0, Math.min(1.0,
                (WIND_COOLING_END_TEMPERATURE - temperature)
                        / (WIND_COOLING_END_TEMPERATURE - WIND_COOLING_FULL_TEMPERATURE)));
    }
}
