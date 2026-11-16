package codeit.ootd.infra.weather;

import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.HourlyItem;
import codeit.ootd.infra.weather.dto.OwmOneCallHourlyResponse.Precipitation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OwmHourlyForecastConverter {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public List<ThreeHourForecast> convert(List<HourlyItem> hourlyItems) {
        Map<Long, HourlyItem> byTime = new HashMap<>();

        for (HourlyItem item : hourlyItems) {
            if (byTime.putIfAbsent(item.dt(), item) != null) {
                throw new IllegalStateException(
                    "OWM 시간별 날씨 데이터에 중복된 시각이 포함되어 있습니다."
                );
            }
        }

        List<ThreeHourForecast> result = new ArrayList<>();

        for (HourlyItem item : hourlyItems) {
            int hour = Instant.ofEpochSecond(item.dt())
                .atZone(KOREA_ZONE)
                .getHour();

            if (hour % 3 != 0) {
                continue;
            }

            if (item.weather() == null || item.weather().isEmpty()) {
                continue;
            }

            boolean midnight = hour == 0;

            HourlyItem previousOne = byTime.get(item.dt() - 3600);
            HourlyItem previousTwo = byTime.get(item.dt() - 7200);

            if (!midnight && (previousOne == null || previousTwo == null)) {
                continue;
            }

            double rainAmount = midnight ? 0.0
                : amount(item.rain())
                    + amount(previousOne.rain())
                    + amount(previousTwo.rain());

            double snowAmount = midnight ? 0.0
                : amount(item.snow())
                  + amount(previousOne.snow())
                  + amount(previousTwo.snow());

            Double pop = null;

            if (!midnight
                && item.pop() != null
                && previousOne.pop() != null
                && previousTwo.pop() != null) {
                pop = Math.max(
                    item.pop(),
                    Math.max(previousOne.pop(), previousTwo.pop())
                );
            }

            result.add(new ThreeHourForecast(
                Instant.ofEpochSecond(item.dt()),
                item.temp(),
                item.humidity(),
                item.weather().get(0).id(),
                pop,
                rainAmount,
                snowAmount,
                item.windSpeed()
            ));
        }

        result.sort(Comparator.comparing(ThreeHourForecast::forecastAt));

        return result;
    }

    private double amount(Precipitation precipitation) {
        return precipitation == null || precipitation.amount() == null
            ? 0.0
            : precipitation.amount();
    }

    public record ThreeHourForecast(
        Instant forecastAt,
        double temperature,
        short humidity,
        int weatherCode,
        Double precipitationProbability,
        double rainAmount,
        double snowAmount,
        double windSpeed
    ) {
    }
}
