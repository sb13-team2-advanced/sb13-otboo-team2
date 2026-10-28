package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherForecastService {

    private final WeatherForecastRepository weatherForecastRepository;

    @Transactional
    public void saveForecasts(
        WeatherLocation location,
        OwmForecastResponse response
    ) {

        Instant collectedAt = Instant.now();

        for (OwmForecastResponse.ForecastItem item : response.list()) {

            Instant forecastAt = Instant.ofEpochSecond(item.dt());

            double temperature = item.main().temp();
            short humidity = item.main().humidity();
            int weatherCode = item.weather().get(0).id();

            double precipitationProbability = item.pop();

            double rainAmount = item.rain() == null
                ? 0.0
                : item.rain().amount();

            double snowAmount = item.snow() == null
                ? 0.0
                : item.snow().amount();

            double windSpeed = item.wind().speed();

            Optional<WeatherForecast> existingForecast =
                weatherForecastRepository.findByLocationIdAndForecastAt(
                    location.getId(),
                    forecastAt
                );

            if (existingForecast.isPresent()) {

                WeatherForecast forecast = existingForecast.get();

                forecast.updateForecast(
                    collectedAt,
                    temperature,
                    humidity,
                    weatherCode,
                    precipitationProbability,
                    rainAmount,
                    snowAmount,
                    windSpeed
                );

                weatherForecastRepository.save(forecast);

            } else {

                WeatherForecast forecast = new WeatherForecast(
                    location.getId(),
                    forecastAt,
                    collectedAt,
                    temperature,
                    humidity,
                    weatherCode,
                    precipitationProbability,
                    rainAmount,
                    snowAmount,
                    windSpeed
                );

                weatherForecastRepository.save(forecast);
            }
        }
    }

}
