package codeit.ootd.domain.weather.batch;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCleanupTasklet implements Tasklet {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Duration FORECAST_RETENTION = Duration.ofDays(7);
    private static final int WEATHER_RETENTION_DAYS = 30;
    private static final int USAGE_RETENTION_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    public RepeatStatus execute(
        @NonNull StepContribution contribution,
        @NonNull ChunkContext chunkContext
    ) {

        Instant now = clock.instant();

        Instant forecastCutoff = now.minus(FORECAST_RETENTION);

        LocalDate weatherCutoff = LocalDate.ofInstant(
            now, KOREA_ZONE
        ).minusDays(WEATHER_RETENTION_DAYS);

        LocalDate usageCutoff = LocalDate.ofInstant(
            now, ZoneOffset.UTC
        ).minusDays(USAGE_RETENTION_DAYS);

        int deletedForecasts = jdbcTemplate.update("""
            DELETE FROM weather_forecast
            WHERE forecast_at < ?
            """,
            Timestamp.from(forecastCutoff)
        );

        int deletedWeathers = jdbcTemplate.update("""
            DELETE FROM weather w
            WHERE w.weather_date < ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM feeds f
                  WHERE f.weather_id = w.id
              )
            """,
            Date.valueOf(weatherCutoff)
        );

        int deletedUsage = jdbcTemplate.update("""
            DELETE FROM weather_one_call_usage
            WHERE usage_date < ?
            """,
            Date.valueOf(usageCutoff)
        );

        int totalDeleted = deletedForecasts
            + deletedWeathers
            + deletedUsage;

        contribution.incrementWriteCount(totalDeleted);

        log.info(
            "날씨 데이터 정리 완료: forecast={}건, weather={}건, oneCallUsage={}건",
            deletedForecasts,
            deletedWeathers,
            deletedUsage
        );

        return RepeatStatus.FINISHED;
    }
}
